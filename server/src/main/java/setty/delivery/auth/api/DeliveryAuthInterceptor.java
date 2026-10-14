package setty.delivery.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import setty.delivery.auth.application.DeliveryMemberRepository;
import setty.delivery.auth.domain.DeliveryMember;
import setty.global.auth.InvalidTokenResponse;

@Component
public class DeliveryAuthInterceptor implements HandlerInterceptor {

    static final String LOGIN_DELIVERY_MEMBER = "loginDeliveryMember";

    private final DeliveryMemberRepository deliveryMemberRepository;

    public DeliveryAuthInterceptor(final DeliveryMemberRepository deliveryMemberRepository) {
        this.deliveryMemberRepository = deliveryMemberRepository;
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

        final DeliveryMember deliveryMember = deliveryMemberRepository.findByToken(header.substring(7)).orElse(null);
        if (deliveryMember == null) {
            InvalidTokenResponse.write(response);
            return false;
        }
        request.setAttribute(LOGIN_DELIVERY_MEMBER, deliveryMember);
        return true;
    }
}
