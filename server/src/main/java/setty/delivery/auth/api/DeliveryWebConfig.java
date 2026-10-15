package setty.delivery.auth.api;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DeliveryWebConfig implements WebMvcConfigurer {

    private final DeliveryAuthInterceptor deliveryAuthInterceptor;
    private final LoginDeliveryMemberArgumentResolver loginDeliveryMemberArgumentResolver;

    public DeliveryWebConfig(
            final DeliveryAuthInterceptor deliveryAuthInterceptor,
            final LoginDeliveryMemberArgumentResolver loginDeliveryMemberArgumentResolver
    ) {
        this.deliveryAuthInterceptor = deliveryAuthInterceptor;
        this.loginDeliveryMemberArgumentResolver = loginDeliveryMemberArgumentResolver;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(deliveryAuthInterceptor)
                .addPathPatterns("/api/delivery/**")
                .excludePathPatterns("/api/delivery/auth/signup", "/api/delivery/auth/login");
    }

    @Override
    public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginDeliveryMemberArgumentResolver);
    }
}
