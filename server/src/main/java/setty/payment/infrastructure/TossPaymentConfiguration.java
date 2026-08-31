package setty.payment.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TossPaymentProperties.class)
public class TossPaymentConfiguration {

    @Bean
    public RestClient tossRestClient(final TossPaymentProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl().trim())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(properties.secretKey().trim()))
                .build();
    }

    private String basicAuth(final String secretKey) {
        // 토스는 "시크릿키 + ':'"를 Base64로 인코딩한 값을 Basic 인증에 사용한다. 비밀번호는 비운다.
        final String encoded = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
