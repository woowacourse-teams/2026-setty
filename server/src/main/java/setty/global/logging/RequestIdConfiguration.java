package setty.global.logging;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class RequestIdConfiguration {

    @Bean
    @Profile("dev")
    @ConditionalOnProperty(name = "setty.observability.listing-timing.enabled", havingValue = "true")
    public FilterRegistrationBean<ListingRequestTimingFilter> listingRequestTimingFilter() {
        final FilterRegistrationBean<ListingRequestTimingFilter> registration =
                new FilterRegistrationBean<>(new ListingRequestTimingFilter());
        registration.setName("listingRequestTimingFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setAsyncSupported(true);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        final FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setName("requestIdFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setAsyncSupported(true);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
