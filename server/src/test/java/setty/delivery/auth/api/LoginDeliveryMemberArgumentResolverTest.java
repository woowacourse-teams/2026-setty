package setty.delivery.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import setty.delivery.auth.domain.DeliveryMember;
import setty.global.auth.LoginDeliveryMember;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

class LoginDeliveryMemberArgumentResolverTest {

    private final LoginDeliveryMemberArgumentResolver resolver = new LoginDeliveryMemberArgumentResolver();

    @Test
    void 인증된_배송원을_받는다() throws Exception {
        final DeliveryMember deliveryMember = mock(DeliveryMember.class);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DeliveryAuthInterceptor.LOGIN_DELIVERY_MEMBER, deliveryMember);

        assertThat(resolver.supportsParameter(parameter())).isTrue();
        assertThat(resolver.resolveArgument(parameter(), null, new ServletWebRequest(request), null))
                .isSameAs(deliveryMember);
    }

    @Test
    void 배송원이_없으면_401이다() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("loginMember", new Object());

        assertThatThrownBy(() -> resolver.resolveArgument(parameter(), null, new ServletWebRequest(request), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    private MethodParameter parameter() throws NoSuchMethodException {
        return new MethodParameter(
                LoginDeliveryMemberArgumentResolverTest.class.getDeclaredMethod("deliveryMember", DeliveryMember.class), 0);
    }

    @SuppressWarnings("unused")
    private void deliveryMember(@LoginDeliveryMember final DeliveryMember deliveryMember) {
    }
}
