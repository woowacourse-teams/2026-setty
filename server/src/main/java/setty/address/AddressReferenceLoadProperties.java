package setty.address;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// rows는 올린 파일의 줄 수다. 표가 이보다 적으면 덜 들어간 것으로 보고 이어 넣는다(비었는지만 보면 중간에 멈춘 표를 영영 못 채운다).
@ConfigurationProperties(prefix = "setty.address.reference.load")
record AddressReferenceLoadProperties(
        boolean enabled,
        String region,
        String bucket,
        String key,
        String sha256,
        long rows,
        Duration timeout,
        int batchSize
) {

    // 한 문장의 자리표시자(16칸 × 줄 수)가 MySQL 한도 65,535를 넘지 않게 한다
    private static final int MAX_BATCH_SIZE = 4_000;

    AddressReferenceLoadProperties {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofMinutes(10);
        }
        if (batchSize <= 0) {
            batchSize = 2_000;
        }
        batchSize = Math.min(batchSize, MAX_BATCH_SIZE);
    }

    String expectedSha256() {
        final String value = sha256 == null ? "" : sha256.trim().toLowerCase();
        if (!value.matches("[0-9a-f]{64}") || isBlank(bucket) || isBlank(key) || rows <= 0) {
            throw new IllegalStateException("주소 기준표 적재에는 bucket, key, sha256(64자), rows(1 이상)를 설정해야 합니다");
        }
        return value;
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
