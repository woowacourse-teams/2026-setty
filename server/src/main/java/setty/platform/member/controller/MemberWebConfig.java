package setty.platform.member.controller;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MemberWebConfig implements WebMvcConfigurer {

    private final MemberAuthInterceptor memberAuthInterceptor;
    private final LoginMemberArgumentResolver loginMemberArgumentResolver;

    public MemberWebConfig(
            final MemberAuthInterceptor memberAuthInterceptor,
            final LoginMemberArgumentResolver loginMemberArgumentResolver
    ) {
        this.memberAuthInterceptor = memberAuthInterceptor;
        this.loginMemberArgumentResolver = loginMemberArgumentResolver;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(memberAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/delivery/**", "/api/auth/signup", "/api/auth/login", "/api/dongs");
    }

    @Override
    public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginMemberArgumentResolver);
    }
}
