package setty.deliveryfee.region;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import setty.deliveryfee.config.ZipTableProperties;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

// 63MB 표를 힙에 올리면 무중단 배포 때 두 JVM이 한 벌씩 들고 있게 된다. 파일을 메모리 매핑하면 두 JVM이 운영체제의
// 페이지 캐시 한 벌을 같이 읽고, 메모리가 모자라면 운영체제가 비웠다가 파일에서 다시 읽는다.
// zone-mode가 zip일 때만 만들어진다. 표를 얻지 못해도 기동은 막지 않고, 쓸 수 없음(available=false)으로 남아 행정동으로 계산한다.
// 값은 10m 단위 uint16, 리틀엔디언. 행은 구매자 구역, 열은 판매자 구역이라 검색 한 번이 한 행(약 11KB)만 읽는다.
@Component
@ConditionalOnProperty(name = "setty.delivery-fee.zone-mode", havingValue = "zip")
public class ZipDistanceTable {

    private static final Logger log = LoggerFactory.getLogger(ZipDistanceTable.class);
    private static final String PREFIX = "seoul-zip-distances-";

    private final ShortBuffer tenMeters;
    private final int size;

    @Autowired
    public ZipDistanceTable(final ZipTableProperties properties, final SeoulZipZones zones) {
        this(properties, zones, () -> S3Client.builder()
                .region(Region.of(properties.region().trim()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build());
    }

    ZipDistanceTable(final ZipTableProperties properties, final SeoulZipZones zones, final Supplier<S3Client> s3) {
        ShortBuffer loaded = null;
        try {
            final Path file = resolve(properties, s3);
            loaded = map(file);
            if (loaded.remaining() != (long) zones.size() * zones.size()) {
                throw new IllegalStateException("우편번호 거리표 크기가 구역 수(" + zones.size() + ")와 다릅니다: " + file);
            }
            log.info("우편번호 거리표를 메모리 매핑했습니다. file={}, zones={}", file, zones.size());
        } catch (final IOException | RuntimeException exception) {
            loaded = null;
            log.warn("우편번호 거리표를 쓰지 못해 행정동으로 계산합니다. key={}, directory={}",
                    properties.key(), properties.directory(), exception);
        }
        this.tenMeters = loaded;
        this.size = zones.size();
    }

    private ZipDistanceTable(final ShortBuffer tenMeters, final int size) {
        this.tenMeters = tenMeters;
        this.size = size;
    }

    public static ZipDistanceTable of(final Path file) {
        try {
            final ShortBuffer mapped = map(file);
            return new ZipDistanceTable(mapped, (int) Math.round(Math.sqrt(mapped.remaining())));
        } catch (final IOException e) {
            throw new UncheckedIOException("우편번호 거리표를 열 수 없습니다: " + file, e);
        }
    }

    public boolean available() {
        return tenMeters != null;
    }

    public int meters(final int sellerZone, final int buyerZone) {
        return Short.toUnsignedInt(tenMeters.get(buyerZone * size + sellerZone)) * 10;
    }

    public int size() {
        return size;
    }

    // 두 슬롯이 동시에 받아도 각자 임시 파일에 받아 해시를 확인한 뒤 같은 이름으로 원자적으로 옮기므로, 덜 받은 파일을 매핑하는 일은 없다.
    private static Path resolve(final ZipTableProperties properties, final Supplier<S3Client> s3) throws IOException {
        final String sha256 = properties.sha256() == null ? "" : properties.sha256().trim().toLowerCase();
        if (!sha256.matches("[0-9a-f]{64}") || properties.directory() == null || properties.directory().isBlank()) {
            throw new IllegalStateException("우편번호 거리표의 해시(sha256)와 폴더(directory)를 설정해야 합니다");
        }
        final Path directory = Path.of(properties.directory().trim());
        final Path target = directory.resolve(PREFIX + sha256.substring(0, 12) + ".bin");
        if (Files.isRegularFile(target) && sha256.equals(sha256Of(target))) {
            return target;
        }
        if (properties.key() == null || properties.key().isBlank()) {
            throw new IllegalStateException("로컬에 맞는 거리표가 없고 받을 S3 키(key)도 없습니다: " + target);
        }
        Files.createDirectories(directory);
        final Path part = Files.createTempFile(directory, PREFIX, ".part");
        try {
            download(properties, s3, part);
            final String actual = sha256Of(part);
            if (!sha256.equals(actual)) {
                throw new IllegalStateException("받은 거리표의 해시가 다릅니다: 기대 " + sha256 + ", 실제 " + actual);
            }
            try {
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (final FileAlreadyExistsException exception) {
                if (!sha256.equals(sha256Of(target))) {
                    throw exception;
                }
            }
            log.info("우편번호 거리표를 S3에서 받았습니다. key={}, file={}", properties.key(), target);
            return target;
        } finally {
            Files.deleteIfExists(part);
        }
    }

    // 받기를 다른 스레드에서 돌리고 제한 시간이 지나면 끊는다. 클라이언트를 닫으면 붙잡힌 연결도 닫힌다.
    private static void download(final ZipTableProperties properties, final Supplier<S3Client> s3, final Path part)
            throws IOException {
        final ExecutorService executor = Executors.newSingleThreadExecutor(
                runnable -> Thread.ofPlatform().name("zip-table-download").daemon().unstarted(runnable));
        try (S3Client client = s3.get()) {
            final Future<Void> download = executor.submit(() -> {
                try (InputStream in = client.getObject(GetObjectRequest.builder()
                        .bucket(properties.bucket().trim()).key(properties.key().trim()).build())) {
                    Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            });
            try {
                download.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (final TimeoutException exception) {
                download.cancel(true);
                throw new IllegalStateException("S3에서 우편번호 거리표를 " + properties.timeout() + " 안에 받지 못했습니다");
            } catch (final ExecutionException exception) {
                if (exception.getCause() instanceof IOException io) {
                    throw io;
                }
                throw new IllegalStateException("S3에서 우편번호 거리표를 받지 못했습니다", exception.getCause());
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("우편번호 거리표 받기가 중단됐습니다", exception);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static ShortBuffer map(final Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            final long cells = channel.size() / Short.BYTES;
            final long side = Math.round(Math.sqrt(cells));
            if (channel.size() % Short.BYTES != 0 || side * side != cells) {
                throw new IllegalStateException("우편번호 거리표가 정사각 행렬이 아닙니다: " + channel.size() + "바이트");
            }
            // 매핑은 채널을 닫아도 유지된다
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer();
        }
    }

    private static String sha256Of(final Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final ByteBuffer buffer = ByteBuffer.allocate(1 << 20);
            while (channel.read(buffer) > 0) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
