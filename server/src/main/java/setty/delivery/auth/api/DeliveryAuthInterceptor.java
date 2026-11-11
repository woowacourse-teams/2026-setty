package setty.delivery.auth.api;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import setty.delivery.auth.application.DeliveryAuthService;
import setty.delivery.auth.application.DeliveryMemberRepository;
import setty.delivery.auth.domain.DeliveryMember;
import setty.global.auth.InvalidTokenResponse;
import setty.global.auth.LoginTokenLifetime;

@Component
public class DeliveryAuthInterceptor implements HandlerInterceptor {

    static final String LOGIN_DELIVERY_MEMBER = "loginDeliveryMember";

    private final DeliveryMemberRepository deliveryMemberRepository;
    private final DeliveryAuthService deliveryAuthService;
    private final LoginTokenLifetime loginTokenLifetime;
    private final MeterRegistry meterRegistry;

    public DeliveryAuthInterceptor(
            final DeliveryMemberRepository deliveryMemberRepository,
            final DeliveryAuthService deliveryAuthService,
            final LoginTokenLifetime loginTokenLifetime,
            final ObjectProvider<MeterRegistry> meterRegistries
    ) {
        this.deliveryMemberRepository = deliveryMemberRepository;
        this.deliveryAuthService = deliveryAuthService;
        this.loginTokenLifetime = loginTokenLifetime;
        this.meterRegistry = meterRegistries.getIfAvailable();
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        final String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            InvalidTokenResponse.write(response);
            return false;
        }

        final String token = header.substring(7);
        final DeliveryMember deliveryMember = meterRegistry == null
                ? deliveryMemberRepository.findByToken(token).orElse(null)
                : meterRegistry.timer("setty.auth.repository.lookup", "repository", "delivery_member",
                        "dispatcher", request.getDispatcherType().name())
                        .record(() -> deliveryMemberRepository.findByToken(token).orElse(null));
        if (deliveryMember == null || loginTokenLifetime.isExpired(deliveryMember.getTokenExpiresAt())) {
            InvalidTokenResponse.write(response);
            return false;
        }
        // 일하는 배송원이 도중에 로그아웃되지 않게, 수명의 절반을 넘긴 토큰은 쓰일 때 만료를 다시 미룬다.
        if (loginTokenLifetime.isPastHalf(deliveryMember.getTokenExpiresAt())) {
            deliveryAuthService.extendTokenInUse(token);
        }
        request.setAttribute(LOGIN_DELIVERY_MEMBER, deliveryMember);
        return true;
    }
}
