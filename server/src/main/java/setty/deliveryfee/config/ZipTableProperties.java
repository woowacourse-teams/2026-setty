package setty.deliveryfee.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// zone-mode가 zip일 때만 쓴다. directory에 seoul-zip-distances-<sha256 앞 12자>.bin이 있고 해시가 맞으면 그 파일을, 없으면 S3에서 받는다.
@ConfigurationProperties(prefix = "setty.delivery-fee.zip-table")
public record ZipTableProperties(
        String region,
        String bucket,
        String key,
        String sha256,
        String directory,
        Duration timeout
) {

    // 앱 시작 때 S3가 멈춰도 배포 헬스 체크 안에 기동이 끝나도록 받기 전체(연결·읽기)에 거는 제한이다
    public ZipTableProperties {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(30);
        }
    }
}
