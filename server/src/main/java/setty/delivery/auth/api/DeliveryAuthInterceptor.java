package setty.delivery.auth.api;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import setty.delivery.auth.application.DeliveryMemberRepository;
import setty.delivery.auth.domain.DeliveryMember;
import setty.global.auth.InvalidTokenResponse;

@Component
public class DeliveryAuthInterceptor implements HandlerInterceptor {

    static final String LOGIN_DELIVERY_MEMBER = "loginDeliveryMember";

    private final DeliveryMemberRepository deliveryMemberRepository;
    private final MeterRegistry meterRegistry;

    public DeliveryAuthInterceptor(
            final DeliveryMemberRepository deliveryMemberRepository,
            final ObjectProvider<MeterRegistry> meterRegistries
    ) {
        this.deliveryMemberRepository = deliveryMemberRepository;
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
        if (deliveryMember == null) {
            InvalidTokenResponse.write(response);
            return false;
        }
        request.setAttribute(LOGIN_DELIVERY_MEMBER, deliveryMember);
        return true;
    }
}
