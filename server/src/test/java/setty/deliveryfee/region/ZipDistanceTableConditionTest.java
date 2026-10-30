package setty.deliveryfee.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import setty.deliveryfee.config.ZipTableProperties;

// 기본값(dong)으로 머지·배포해도 서버의 S3 접근·디스크·폴더·메모리가 바뀌지 않아야 한다.
class ZipDistanceTableConditionTest {

    private static final String SHA256 = "996e78f51ae9" + "0".repeat(52);

    @TempDir
    Path dir;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class, SeoulZipZones.class, ZipDistanceTable.class);

    @Test
    void 구역_방식을_정하지_않으면_거리표를_만들지_않고_폴더도_만들지_않는다() {
        final Path data = dir.resolve("data");

        runner.withPropertyValues(zipTable(data))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ZipDistanceTable.class));

        assertThat(Files.exists(data)).isFalse();
    }

    @Test
    void 행정동_방식이면_설정이_비어도_시작한다() {
        runner.withPropertyValues("setty.delivery-fee.zone-mode=dong")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ZipDistanceTable.class));
    }

    @Test
    void 우편번호_방식인데_거리표를_얻지_못해도_기동은_막지_않는다() {
        final Path data = dir.resolve("data");

        runner.withPropertyValues(zipTable(data))
                .withPropertyValues("setty.delivery-fee.zone-mode=zip", "setty.delivery-fee.zip-table.key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ZipDistanceTable.class).available()).isFalse();
                });
        assertThat(Files.exists(data)).isFalse();
    }

    private String[] zipTable(final Path directory) {
        return new String[]{
                "setty.delivery-fee.zip-table.region=ap-northeast-2",
                "setty.delivery-fee.zip-table.bucket=bucket",
                "setty.delivery-fee.zip-table.key=setty/deliveryfee/seoul-zip-distances.bin",
                "setty.delivery-fee.zip-table.sha256=" + SHA256,
                "setty.delivery-fee.zip-table.directory=" + directory
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZipTableProperties.class)
    static class Properties {
    }
}
