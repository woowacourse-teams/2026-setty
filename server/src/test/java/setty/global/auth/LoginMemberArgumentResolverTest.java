package setty.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import setty.platform.member.domain.Member;

class LoginMemberArgumentResolverTest {

    private final LoginMemberArgumentResolver resolver = new LoginMemberArgumentResolver();

    @Test
    void 회원_ID_인자는_인증된_회원의_ID를_받는다() throws Exception {
        final Member member = mock(Member.class);
        when(member.getId()).thenReturn(42L);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthInterceptor.LOGIN_MEMBER, member);
        final MethodParameter parameter = new MethodParameter(
                LoginMemberArgumentResolverTest.class.getDeclaredMethod("memberId", Long.class), 0);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
        assertThat(resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null)).isEqualTo(42L);
    }

    @SuppressWarnings("unused")
    private void memberId(@LoginMember final Long memberId) {
    }
}
