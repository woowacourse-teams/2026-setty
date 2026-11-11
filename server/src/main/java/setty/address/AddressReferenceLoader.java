package setty.address;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

// 레포가 공개라 주소 기준표(약 52만 줄)는 비공개 S3에만 두고, 표가 덜 찬 DB에 앱이 한 번 넣는다.
// 파일 전체를 메모리·디스크에 두지 않도록 S3 스트림 → gunzip → 줄 → 여러 줄 INSERT로 흘려 넣는다.
// 해시를 확인한 뒤에만 넣으려고 S3를 두 번 읽는다. 첫 번째는 해시만 계산하고, 두 번째는 같은 ETag일 때만 받는다.
@Component
@ConditionalOnProperty(name = "setty.address.reference.load.enabled", havingValue = "true")
@EnableConfigurationProperties(AddressReferenceLoadProperties.class)
class AddressReferenceLoader {

    private static final Logger log = LoggerFactory.getLogger(AddressReferenceLoader.class);
    private static final String LOCK = "setty.address_reference.load";
    private static final int COLUMNS = 16;
    private static final String INSERT = """
            INSERT INTO address_reference (mgmt_no, road_code, road_name, underground, building_main, building_sub,
                sigungu, legal_dong, jibun_mountain, jibun_main, jibun_sub, zip_code, dong_code, building_name,
                latitude, longitude)
            VALUES\s""";
    private static final String ROW = "(" + "?, ".repeat(COLUMNS - 1) + "?)";
    // 멈췄다가 다시 돌 때 이미 들어간 관리번호는 그대로 둔다. INSERT IGNORE와 달리 형식 오류는 경고로 삼키지 않는다.
    private static final String ON_DUPLICATE = " ON DUPLICATE KEY UPDATE mgmt_no = mgmt_no";
    private static final String NULL = "\\N";

    enum Result { LOADED, ALREADY_LOADED, LOCKED_BY_OTHER, FAILED }

    private final AddressReferenceLoadProperties properties;
    private final DataSource dataSource;
    private final Supplier<S3Client> s3;

    @Autowired
    AddressReferenceLoader(final AddressReferenceLoadProperties properties, final DataSource dataSource) {
        this(properties, dataSource, () -> S3Client.builder()
                .region(Region.of(properties.region().trim()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build());
    }

    AddressReferenceLoader(
            final AddressReferenceLoadProperties properties,
            final DataSource dataSource,
            final Supplier<S3Client> s3
    ) {
        this.properties = properties;
        this.dataSource = dataSource;
        this.s3 = s3;
    }

    // 무중단 배포 헬스 체크 안에 앱이 떠야 하므로 시작을 막지 않고 전용 스레드에서 넣는다
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread.ofPlatform().name("address-reference-load").daemon().start(this::load);
    }

    Result load() {
        final long startedAt = System.nanoTime();
        try {
            final String sha256 = properties.expectedSha256();
            try (Connection connection = dataSource.getConnection()) {
                // 무중단 배포 중 두 슬롯이 함께 떠도 한쪽만 넣는다. 잠금은 이 연결(세션)에 묶여 있어 프로세스가 죽으면 풀린다.
                if (!tryLock(connection)) {
                    log.info("다른 인스턴스가 주소 기준표를 적재하고 있어 건너뜁니다.");
                    return Result.LOCKED_BY_OTHER;
                }
                try {
                    return loadLocked(connection, sha256, startedAt);
                } finally {
                    release(connection);
                }
            }
        } catch (final IOException | SQLException | RuntimeException exception) {
            log.warn("주소 기준표를 적재하지 못했습니다. 앱은 그대로 동작하고, 다음 시작 때 이어서 넣습니다. key={}, elapsedMillis={}",
                    properties.key(), elapsedMillis(startedAt), exception);
            return Result.FAILED;
        }
    }

    private Result loadLocked(final Connection connection, final String sha256, final long startedAt)
            throws IOException, SQLException {
        final long before = count(connection);
        if (before >= properties.rows()) {
            log.info("주소 기준표가 이미 차 있어 건너뜁니다. rows={}", before);
            return Result.ALREADY_LOADED;
        }
        final long lines = copy(connection, sha256);
        final long after = count(connection);
        log.info("주소 기준표를 S3에서 적재했습니다. key={}, lines={}, before={}, after={}, elapsedMillis={}",
                properties.key(), lines, before, after, elapsedMillis(startedAt));
        if (after < properties.rows()) {
            log.warn("주소 기준표 행 수가 설정한 rows보다 적습니다. after={}, rows={}", after, properties.rows());
        }
        return Result.LOADED;
    }

    private long copy(final Connection connection, final String sha256) throws IOException, SQLException {
        try (S3Client client = s3.get(); Deadline deadline = new Deadline(properties.timeout())) {
            try {
                final String eTag;
                try (ResponseInputStream<GetObjectResponse> in = deadline.watch(client.getObject(request(null)))) {
                    final String actual = sha256Of(in);
                    if (!sha256.equals(actual)) {
                        throw new IllegalStateException("받은 주소 기준표의 해시가 다릅니다: 기대 " + sha256 + ", 실제 " + actual);
                    }
                    eTag = in.response().eTag();
                }
                deadline.check();
                try (ResponseInputStream<GetObjectResponse> in = deadline.watch(client.getObject(request(eTag)));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(
                             new GZIPInputStream(in, 1 << 16), StandardCharsets.UTF_8), 1 << 16)) {
                    return insert(connection, reader, deadline);
                }
            } catch (final IOException | RuntimeException exception) {
                if (deadline.expired()) {
                    throw new IllegalStateException("주소 기준표를 " + properties.timeout() + " 안에 적재하지 못했습니다", exception);
                }
                throw exception;
            }
        }
    }

    // 해시를 확인한 뒤 파일이 바뀌었으면 받지 않는다(If-Match)
    private GetObjectRequest request(final String eTag) {
        return GetObjectRequest.builder()
                .bucket(properties.bucket().trim())
                .key(properties.key().trim())
                .ifMatch(eTag)
                .build();
    }

    private long insert(final Connection connection, final BufferedReader reader, final Deadline deadline)
            throws IOException, SQLException {
        final int batchSize = properties.batchSize();
        final List<String[]> rows = new ArrayList<>(batchSize);
        long lines = 0;
        try (PreparedStatement full = connection.prepareStatement(insertSql(batchSize))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                lines++;
                rows.add(parse(line, lines));
                if (rows.size() == batchSize) {
                    deadline.check();
                    execute(full, rows);
                    rows.clear();
                }
            }
        }
        if (!rows.isEmpty()) {
            try (PreparedStatement rest = connection.prepareStatement(insertSql(rows.size()))) {
                execute(rest, rows);
            }
        }
        return lines;
    }

    // 줄은 V7 칸 순서의 TSV, NULL은 \N (load_reference.sql의 LOAD DATA와 같은 형식)
    private static String[] parse(final String line, final long lineNumber) {
        final String[] values = line.split("\t", -1);
        if (values.length != COLUMNS) {
            throw new IllegalStateException(
                    "주소 기준표 " + lineNumber + "번째 줄의 칸 수가 " + values.length + "개입니다(기대 " + COLUMNS + "개)");
        }
        for (int i = 0; i < values.length; i++) {
            if (NULL.equals(values[i])) {
                values[i] = null;
            }
        }
        return values;
    }

    private static void execute(final PreparedStatement statement, final List<String[]> rows) throws SQLException {
        int index = 1;
        for (final String[] row : rows) {
            for (final String value : row) {
                statement.setString(index++, value);
            }
        }
        statement.executeUpdate();
    }

    private static String insertSql(final int rows) {
        return INSERT + String.join(", ", Collections.nCopies(rows, ROW)) + ON_DUPLICATE;
    }

    private static boolean tryLock(final Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, LOCK);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    // 풀로 돌아간 연결에 잠금이 남지 않게 한다. 풀지 못하면 연결을 버려 세션과 함께 잠금이 풀리게 하고,
    // 원래 실패 원인을 덮지 않도록 예외를 던지지 않는다.
    private static void release(final Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, LOCK);
            statement.executeQuery().close();
        } catch (final SQLException exception) {
            log.warn("주소 기준표 적재 잠금을 풀지 못해 연결을 버립니다.", exception);
            try {
                connection.abort(Runnable::run);
            } catch (final SQLException abortFailure) {
                log.warn("주소 기준표 적재 연결을 버리지 못했습니다.", abortFailure);
            }
        }
    }

    private static long count(final Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM address_reference");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private static String sha256Of(final InputStream in) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long elapsedMillis(final long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    // 받기·적재 전체에 거는 시간 제한. 시간이 되면 읽던 S3 스트림을 끊어, 응답이 멈춘 읽기도 풀리게 한다.
    private static final class Deadline implements AutoCloseable {

        private final long deadlineNanos;
        private final AtomicReference<ResponseInputStream<?>> current = new AtomicReference<>();
        private final CompletableFuture<Void> timer;

        Deadline(final Duration timeout) {
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
            this.timer = CompletableFuture.runAsync(this::abort,
                    CompletableFuture.delayedExecutor(timeout.toMillis(), TimeUnit.MILLISECONDS));
        }

        <T extends ResponseInputStream<?>> T watch(final T stream) {
            current.set(stream);
            if (expired()) {
                stream.abort();
            }
            return stream;
        }

        boolean expired() {
            return System.nanoTime() - deadlineNanos >= 0;
        }

        void check() {
            if (expired()) {
                throw new IllegalStateException("주소 기준표 적재 시간이 지났습니다");
            }
        }

        private void abort() {
            final ResponseInputStream<?> stream = current.get();
            if (stream != null) {
                stream.abort();
            }
        }

        @Override
        public void close() {
            timer.cancel(false);
        }
    }
}
