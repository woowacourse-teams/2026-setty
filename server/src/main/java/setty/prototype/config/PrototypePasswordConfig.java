package setty.prototype.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시에만 spring-security-crypto를 쓴다.
 * 필터 체인·자동 설정이 붙는 spring-boot-starter-security는 넣지 않는다.
 */
@Configuration
public class PrototypePasswordConfig {
    @Bean
    public PasswordEncoder prototypePasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
