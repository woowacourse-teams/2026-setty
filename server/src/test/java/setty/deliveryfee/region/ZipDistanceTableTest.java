package setty.deliveryfee.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import setty.deliveryfee.DeliveryFeeService;
import setty.deliveryfee.VolumeTier;
import setty.deliveryfee.config.DeliveryFeeProperties;
import setty.deliveryfee.config.ZipTableProperties;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class ZipDistanceTableTest {

    // 구역 2개: [구매자][판매자] = 판매자 1 → 구매자 0은 120(1.2km), 판매자 0 → 구매자 1은 150(1.5km)
    private static final byte[] TABLE = bytes(3, 120, 150, 4);
    private static final SeoulZipZones TWO_ZONES = new SeoulZipZones(List.of("01000", "01001"));

    @TempDir
    Path dir;

    @Test
    void 행은_구매자_열은_판매자로_10m_단위_거리를_읽는다() throws IOException {
        final ZipDistanceTable table = ZipDistanceTable.of(Files.write(dir.resolve("t.bin"), TABLE));

        assertThat(table.size()).isEqualTo(2);
        assertThat(table.meters(1, 0)).isEqualTo(1_200);
        assertThat(table.meters(0, 1)).isEqualTo(1_500);
        assertThat(table.meters(0, 0)).isEqualTo(30);
    }

    @Test
    void 부호_없는_2바이트로_읽는다() throws IOException {
        assertThat(ZipDistanceTable.of(Files.write(dir.resolve("t.bin"), bytes(65_535))).meters(0, 0)).isEqualTo(655_350);
    }

    @Test
    void 정사각_행렬이_아니면_읽지_않는다() throws IOException {
        final Path file = Files.write(dir.resolve("t.bin"), bytes(1, 2, 3));

        assertThatThrownBy(() -> ZipDistanceTable.of(file)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ZipDistanceTable.of(dir.resolve("absent.bin"))).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void 해시가_맞는_파일이_폴더에_있으면_S3에_묻지_않는다() throws IOException {
        Files.write(dir.resolve(fileName(TABLE)), TABLE);
        final S3Client s3 = mock(S3Client.class);

        final ZipDistanceTable table = new ZipDistanceTable(properties(sha256(TABLE)), TWO_ZONES, () -> s3);

        assertThat(table.available()).isTrue();
        assertThat(table.meters(1, 0)).isEqualTo(1_200);
        verify(s3, times(0)).getObject(any(GetObjectRequest.class));
    }

    @Test
    void 파일이_없으면_S3에서_받아_해시를_확인하고_같은_이름으로_둔다() throws IOException {
        final ZipDistanceTable table = new ZipDistanceTable(properties(sha256(TABLE)), TWO_ZONES, () -> s3Returning(TABLE));

        assertThat(table.available()).isTrue();
        assertThat(table.meters(0, 1)).isEqualTo(1_500);
        assertThat(files()).containsExactly(fileName(TABLE));
    }

    @Test
    void 받은_파일의_해시가_다르면_쓰지_않고_파일도_남기지_않는다() throws IOException {
        final byte[] other = bytes(3, 999, 150, 4);

        final ZipDistanceTable table = new ZipDistanceTable(properties(sha256(TABLE)), TWO_ZONES, () -> s3Returning(other));

        assertThat(table.available()).isFalse();
        assertThat(files()).isEmpty();
    }

    @Test
    void 해시가_다른_옛_파일이_있으면_다시_받는다() throws IOException {
        Files.write(dir.resolve(fileName(TABLE)), bytes(1, 1, 1, 1));

        final ZipDistanceTable table = new ZipDistanceTable(properties(sha256(TABLE)), TWO_ZONES, () -> s3Returning(TABLE));

        assertThat(table.available()).isTrue();
        assertThat(Files.readAllBytes(dir.resolve(fileName(TABLE)))).isEqualTo(TABLE);
    }

    @Test
    void S3에서_받지_못하면_기동을_막지_않고_쓸_수_없음으로_남는다() throws IOException {
        final S3Client s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenThrow(SdkClientException.create("접근 거부"));

        final ZipDistanceTable table = new ZipDistanceTable(properties(sha256(TABLE)), TWO_ZONES, () -> s3);

        assertThat(table.available()).isFalse();
        assertThat(files()).isEmpty();
    }

    @Test
    void S3가_제한_시간_안에_답하지_않으면_끊고_쓸_수_없음으로_남는다() throws IOException {
        final S3Client s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(10_000);
            return null;
        });
        final long startedAt = System.nanoTime();

        final ZipDistanceTable table = new ZipDistanceTable(
                properties(sha256(TABLE), Duration.ofMillis(200)), TWO_ZONES, () -> s3);

        assertThat(table.available()).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
        assertThat(files()).isEmpty();
    }

    @Test
    void 제한_시간을_정하지_않으면_30초다() {
        assertThat(properties(sha256(TABLE)).timeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void 해시를_설정하지_않으면_S3와_폴더를_건드리지_않고_쓸_수_없음으로_남는다() {
        final Path absent = dir.resolve("data");
        final Supplier<S3Client> s3 = () -> {
            throw new AssertionError("S3 클라이언트를 만들면 안 됩니다");
        };

        final ZipDistanceTable table = new ZipDistanceTable(
                new ZipTableProperties("ap-northeast-2", "bucket", "key", "", absent.toString(), null), TWO_ZONES, s3);

        assertThat(table.available()).isFalse();
        assertThat(Files.exists(absent)).isFalse();
    }

    @Test
    void 구역_수와_크기가_다르면_쓰지_않는다() throws IOException {
        Files.write(dir.resolve(fileName(TABLE)), TABLE);

        final ZipDistanceTable table = new ZipDistanceTable(
                properties(sha256(TABLE)), new SeoulZipZones(List.of("01000", "01001", "01002")), () -> s3Returning(TABLE));

        assertThat(table.available()).isFalse();
    }

    @Test
    void 두_슬롯이_동시에_받아도_둘_다_완전한_파일을_쓴다() throws IOException {
        final byte[] big = new byte[2 * 1_000 * 1_000];
        for (int i = 0; i < big.length; i += 2) {
            big[i] = (byte) (i / 2 % 251);
        }
        final SeoulZipZones zones = new SeoulZipZones(
                Stream.iterate(1_000, i -> i + 1).limit(1_000).map(i -> String.format("%05d", i)).toList());
        final ZipTableProperties properties = properties(sha256(big));

        final List<ZipDistanceTable> tables = Stream.of(1, 2)
                .map(slot -> CompletableFuture.supplyAsync(() -> new ZipDistanceTable(properties, zones, () -> s3Returning(big))))
                .toList().stream().map(CompletableFuture::join).toList();

        assertThat(tables).allSatisfy(table -> {
            assertThat(table.available()).isTrue();
            assertThat(table.meters(10, 999)).isEqualTo((999 * 1_000 + 10) % 251 * 10);
        });
        assertThat(files()).containsExactly(fileName(big));
    }

    @Test
    void 쓸_수_없는_거리표면_배송비를_행정동으로_계산한다() {
        final ZipDistanceTable unavailable = new ZipDistanceTable(
                new ZipTableProperties("ap-northeast-2", "bucket", "", "", dir.toString(), null), TWO_ZONES, () -> null);

        final DeliveryFeeService service = new DeliveryFeeService(new SeoulDongs(), new DistanceTable(), TWO_ZONES,
                Optional.of(unavailable), new DeliveryFeeProperties(10_000, 5_000, 2, 3_000, 3_000));

        assertThat(service.zipEnabled()).isFalse();
        assertThat(service.zipFee(0, 1, VolumeTier.SMALL)).isEmpty();
    }

    private ZipTableProperties properties(final String sha256) {
        return properties(sha256, null);
    }

    private ZipTableProperties properties(final String sha256, final Duration timeout) {
        return new ZipTableProperties("ap-northeast-2", "bucket", "setty/deliveryfee/seoul-zip-distances.bin", sha256,
                dir.toString(), timeout);
    }

    private static S3Client s3Returning(final byte[] body) {
        final S3Client s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), AbortableInputStream.create(new ByteArrayInputStream(body))));
        return s3;
    }

    private List<String> files() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static String fileName(final byte[] table) {
        return "seoul-zip-distances-" + sha256(table).substring(0, 12) + ".bin";
    }

    private static String sha256(final byte[] table) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(table));
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytes(final int... tenMeters) {
        final ByteBuffer buffer = ByteBuffer.allocate(tenMeters.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (final int value : tenMeters) {
            buffer.putShort((short) value);
        }
        return buffer.array();
    }
}
