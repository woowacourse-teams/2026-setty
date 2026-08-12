package setty.common.operator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OperatorAuthInterceptor implements HandlerInterceptor {
    public static final String OPERATOR_SECRET_HEADER = "X-Operator-Secret";

    private final OperatorAuthProperties operatorAuthProperties;

    public OperatorAuthInterceptor(final OperatorAuthProperties operatorAuthProperties) {
        this.operatorAuthProperties = operatorAuthProperties;
    }

    @Override
    public boolean preHandle(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Object handler
    ) {
        // preflight에는 브라우저가 인증 헤더를 싣지 않는다. 여기서 막으면 본 요청 자체가 오지 않는다.
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        final String configuredSecret = operatorAuthProperties.secret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new UnauthorizedOperatorException();
        }

        final String presentedSecret = request.getHeader(OPERATOR_SECRET_HEADER);
        if (presentedSecret == null || !matches(configuredSecret, presentedSecret)) {
            throw new UnauthorizedOperatorException();
        }

        return true;
    }

    private boolean matches(final String configuredSecret, final String presentedSecret) {
        return MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                presentedSecret.getBytes(StandardCharsets.UTF_8)
        );
    }
}
