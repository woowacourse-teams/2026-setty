package setty.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import setty.address.AddressReferenceLoader.Result;
import setty.support.MySqlIntegrationTestSupport;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

// 자료는 테스트가 만든 가짜 주소다. 실제 주소·좌표는 레포에 두지 않는다.
@SpringBootTest
class AddressReferenceLoaderTest extends MySqlIntegrationTestSupport {

    private static final int ROWS = 5_000;
    private static final List<String> LINES = lines(ROWS);
    private static final byte[] GZ = gzip(String.join("\n", LINES) + "\n");
    private static final String SHA256 = sha256(GZ);
    private static final String ETAG = "\"etag-1\"";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM address_reference");
    }

    @Test
    void 꺼져_있으면_적재기를_만들지_않는다() {
        new ApplicationContextRunner()
                .withUserConfiguration(AddressReferenceLoader.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AddressReferenceLoader.class));
        new ApplicationContextRunner()
                .withUserConfiguration(AddressReferenceLoader.class)
                .withPropertyValues("setty.address.reference.load.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AddressReferenceLoader.class));
    }

    @Test
    void 켜면_설정을_읽어_적재기를_만든다() {
        new ApplicationContextRunner()
                .withUserConfiguration(AddressReferenceLoader.class)
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues(
                        "setty.address.reference.load.enabled=true",
                        "setty.address.reference.load.region=ap-northeast-2",
                        "setty.address.reference.load.rows=523227",
                        "setty.address.reference.load.batch-size=9000")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AddressReferenceLoader.class);
                    final AddressReferenceLoadProperties properties =
                            context.getBean(AddressReferenceLoadProperties.class);
                    assertThat(properties.rows()).isEqualTo(523_227);
                    assertThat(properties.batchSize()).isEqualTo(4_000);
                    assertThat(properties.timeout()).isEqualTo(Duration.ofMinutes(10));
                });
    }

    @Test
    void 비어_있으면_해시를_확인하고_같은_파일일_때만_받아_넣는다() {
        final S3Client s3 = s3Serving(GZ);

        assertThat(loader(s3, SHA256).load()).isEqualTo(Result.LOADED);

        assertThat(table()).isEqualTo(expectedTable());
        final ArgumentCaptor<GetObjectRequest> requests = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3, times(2)).getObject(requests.capture());
        assertThat(requests.getAllValues().get(0).ifMatch()).isNull();
        assertThat(requests.getAllValues().get(1).ifMatch()).isEqualTo(ETAG);
        assertLockFree();
    }

    @Test
    void 이미_차_있으면_S3에_묻지_않는다() {
        loader(s3Serving(GZ), SHA256).load();
        final S3Client s3 = mock(S3Client.class);

        assertThat(loader(s3, SHA256).load()).isEqualTo(Result.ALREADY_LOADED);

        verifyNoInteractions(s3);
        assertThat(count()).isEqualTo(ROWS);
    }

    @Test
    void 해시가_다르면_넣지_않는다() {
        final S3Client s3 = s3Serving(GZ);

        assertThat(loader(s3, "0".repeat(64)).load()).isEqualTo(Result.FAILED);

        verify(s3, times(1)).getObject(any(GetObjectRequest.class));
        assertThat(count()).isZero();
        assertLockFree();
    }

    @Test
    void 해시를_확인한_뒤_파일이_바뀌면_넣지_않는다() {
        final S3Client s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenAnswer(invocation -> response(new ByteArrayInputStream(GZ), () -> { }))
                .thenThrow(S3Exception.builder().statusCode(412).message("Precondition Failed").build());

        assertThat(loader(s3, SHA256).load()).isEqualTo(Result.FAILED);

        verify(s3, times(2)).getObject(any(GetObjectRequest.class));
        assertThat(count()).isZero();
        assertLockFree();
    }

    @Test
    void 설정이_비면_넣지_않고_S3에_묻지_않는다() {
        final S3Client s3 = mock(S3Client.class);

        assertThat(loader(s3, "").load()).isEqualTo(Result.FAILED);

        verifyNoInteractions(s3);
        assertThat(count()).isZero();
    }

    @Test
    void 두_인스턴스가_동시에_돌면_한쪽만_넣는다() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final S3Client first = mock(S3Client.class);
        when(first.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return response(new ByteArrayInputStream(GZ), () -> { });
        });
        final S3Client second = mock(S3Client.class);

        final CompletableFuture<Result> running = CompletableFuture.supplyAsync(() -> loader(first, SHA256).load());
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        final Result whileRunning = loader(second, SHA256).load();
        release.countDown();

        assertThat(whileRunning).isEqualTo(Result.LOCKED_BY_OTHER);
        assertThat(running.get(30, TimeUnit.SECONDS)).isEqualTo(Result.LOADED);
        verifyNoInteractions(second);
        assertThat(loader(second, SHA256).load()).isEqualTo(Result.ALREADY_LOADED);
        assertThat(table()).isEqualTo(expectedTable());
    }

    @Test
    void 중간에_끊겨도_다시_돌리면_같은_결과가_된다() {
        final S3Client broken = mock(S3Client.class);
        when(broken.getObject(any(GetObjectRequest.class)))
                .thenAnswer(invocation -> response(new ByteArrayInputStream(GZ), () -> { }))
                .thenAnswer(invocation -> response(new FailingAfter(GZ, GZ.length / 2), () -> { }));

        assertThat(loader(broken, SHA256, 100).load()).isEqualTo(Result.FAILED);
        final long partial = count();
        assertThat(partial).isPositive().isLessThan(ROWS);

        assertLockFree();

        assertThat(loader(s3Serving(GZ), SHA256, 100).load()).isEqualTo(Result.LOADED);
        assertThat(table()).isEqualTo(expectedTable());
        assertLockFree();
    }

    @Test
    void 시간이_넘으면_읽던_스트림을_끊고_넣지_않는다() {
        final CountDownLatch aborted = new CountDownLatch(1);
        final S3Client s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> response(new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    aborted.await(10, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new IOException("aborted");
            }
        }, aborted::countDown));
        final AddressReferenceLoader loader = new AddressReferenceLoader(
                properties(SHA256, Duration.ofMillis(300), 2_000), dataSource, () -> s3);

        final long startedAt = System.nanoTime();
        assertThat(loader.load()).isEqualTo(Result.FAILED);

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(5));
        assertThat(count()).isZero();
        assertLockFree();
    }

    private AddressReferenceLoader loader(final S3Client s3, final String sha256) {
        return loader(s3, sha256, 2_000);
    }

    private AddressReferenceLoader loader(final S3Client s3, final String sha256, final int batchSize) {
        return new AddressReferenceLoader(properties(sha256, Duration.ofSeconds(30), batchSize), dataSource, () -> s3);
    }

    private static AddressReferenceLoadProperties properties(
            final String sha256, final Duration timeout, final int batchSize) {
        return new AddressReferenceLoadProperties(true, "ap-northeast-2", "bucket",
                "address/address_reference.tsv.gz", sha256, ROWS, timeout, batchSize);
    }

    private static S3Client s3Serving(final byte[] body) {
        final S3Client s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenAnswer(invocation -> response(new ByteArrayInputStream(body), () -> { }));
        return s3;
    }

    private static ResponseInputStream<GetObjectResponse> response(final InputStream body, final Runnable onAbort) {
        return new ResponseInputStream<>(GetObjectResponse.builder().eTag(ETAG).build(),
                AbortableInputStream.create(body, onAbort::run));
    }

    // 적재기가 쓴 연결은 풀로 돌아가 남아 있으므로, 잠금을 풀지 않았다면 다른 세션에서 보이지 않는다
    private void assertLockFree() {
        assertThat(jdbcTemplate.queryForObject("SELECT IS_FREE_LOCK('setty.address_reference.load')", Integer.class))
                .isEqualTo(1);
    }

    private long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM address_reference", Long.class);
    }

    // 넣은 값을 TSV와 같은 모양으로 다시 읽는다(NULL은 \N, 소수는 6자리)
    private List<String> table() {
        return jdbcTemplate.query("""
                SELECT CONCAT_WS('\t', mgmt_no, road_code, road_name, underground, building_main, building_sub, sigungu,
                    IFNULL(legal_dong, '\\\\N'), jibun_mountain, jibun_main, jibun_sub, zip_code,
                    IFNULL(dong_code, '\\\\N'), IFNULL(building_name, '\\\\N'),
                    IFNULL(latitude, '\\\\N'), IFNULL(longitude, '\\\\N'))
                FROM address_reference ORDER BY mgmt_no
                """, (rs, rowNum) -> rs.getString(1));
    }

    private static List<String> expectedTable() {
        return LINES.stream().sorted().toList();
    }

    private static List<String> lines(final int rows) {
        return IntStream.range(0, rows)
                .mapToObj(i -> String.join("\t",
                        "11%023d".formatted((long) i * 7_919 % 100_003 * 10_000 + i),
                        "%012d".formatted(111_102_005_001L + i % 400),
                        "가짜로" + i % 97,
                        String.valueOf(i % 2),
                        String.valueOf(i * 13 % 900 + 1),
                        String.valueOf(i % 3),
                        "가짜구",
                        i % 5 == 0 ? "\\N" : "가짜동" + i % 11,
                        String.valueOf(i % 7 == 0 ? 1 : 0),
                        String.valueOf(i * 31 % 700 + 1),
                        String.valueOf(i % 4),
                        "%05d".formatted(i * 37 % 100_000),
                        i % 6 == 0 ? "\\N" : "%010d".formatted(1_100_000_000L + i % 424),
                        i % 4 == 0 ? "\\N" : "가짜건물" + i,
                        i % 10 == 0 ? "\\N" : "37.%06d".formatted(i * 211 % 1_000_000),
                        i % 10 == 0 ? "\\N" : "126.%06d".formatted(i * 307 % 1_000_000)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static byte[] gzip(final String text) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // 앞부분만 조금씩 내주다 끊기는 S3 응답
    private static final class FailingAfter extends InputStream {

        private final byte[] body;
        private final int limit;
        private int position;

        FailingAfter(final byte[] body, final int limit) {
            this.body = body;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            final byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            if (position >= limit) {
                throw new IOException("connection reset");
            }
            final int count = Math.min(Math.min(length, 512), limit - position);
            System.arraycopy(body, position, buffer, offset, count);
            position += count;
            return count;
        }
    }
}
