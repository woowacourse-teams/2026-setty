package setty.global.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import setty.delivery.auth.domain.DeliveryMember;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Component
public class LoginDeliveryMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(final MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginDeliveryMember.class)
                && DeliveryMember.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(final MethodParameter parameter, final ModelAndViewContainer mavContainer,
                                  final NativeWebRequest webRequest, final WebDataBinderFactory binderFactory) {
        final Object deliveryMember = webRequest.getAttribute(AuthInterceptor.LOGIN_DELIVERY_MEMBER, RequestAttributes.SCOPE_REQUEST);
        if (deliveryMember == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return deliveryMember;
    }
}
