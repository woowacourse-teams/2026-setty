package setty.common.operator;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OperatorAuthConfig implements WebMvcConfigurer {
    private final OperatorAuthInterceptor operatorAuthInterceptor;

    public OperatorAuthConfig(final OperatorAuthInterceptor operatorAuthInterceptor) {
        this.operatorAuthInterceptor = operatorAuthInterceptor;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(operatorAuthInterceptor)
                .addPathPatterns("/api/operator/**");
    }
}
