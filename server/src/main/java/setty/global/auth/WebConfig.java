package setty.global.auth;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final LoginMemberArgumentResolver loginMemberArgumentResolver;
    private final LoginDeliveryMemberArgumentResolver loginDeliveryMemberArgumentResolver;

    public WebConfig(
            final AuthInterceptor authInterceptor,
            final LoginMemberArgumentResolver loginMemberArgumentResolver,
            final LoginDeliveryMemberArgumentResolver loginDeliveryMemberArgumentResolver
    ) {
        this.authInterceptor = authInterceptor;
        this.loginMemberArgumentResolver = loginMemberArgumentResolver;
        this.loginDeliveryMemberArgumentResolver = loginDeliveryMemberArgumentResolver;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/signup", "/api/auth/login",
                        "/api/delivery/auth/signup", "/api/delivery/auth/login"
                );
    }

    @Override
    public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginMemberArgumentResolver);
        resolvers.add(loginDeliveryMemberArgumentResolver);
    }
}
