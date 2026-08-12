package setty.common.s3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

class S3ConfigTest {
    @Test
    void s3ClientIsConfiguredWithTheConfiguredRegion() {
        final S3Properties s3Properties = new S3Properties("ap-northeast-2", "techcourse-project-2026");
        final S3Client s3Client = new S3Config().s3Client(s3Properties);

        assertThat(s3Client.serviceClientConfiguration().region()).isEqualTo(Region.AP_NORTHEAST_2);
        assertThat(s3Properties.bucketName()).isEqualTo("techcourse-project-2026");
    }
}
