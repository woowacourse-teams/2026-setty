package setty.global.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.member.domain.Member;

@Component
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(final MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMember.class)
                && Member.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(final MethodParameter parameter, final ModelAndViewContainer mavContainer,
                                  final NativeWebRequest webRequest, final WebDataBinderFactory binderFactory) {
        final Object member = webRequest.getAttribute(AuthInterceptor.LOGIN_MEMBER, RequestAttributes.SCOPE_REQUEST);
        if (member == null) {
            // 배송원 토큰 등 member가 아닌 인증으로 플랫폼 API에 진입한 경우 — 500(NPE) 대신 401
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return member;
    }
}
