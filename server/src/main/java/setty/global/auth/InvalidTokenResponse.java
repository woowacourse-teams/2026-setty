package setty.global.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.modulith.NamedInterface;
import setty.global.exception.ErrorCode;

@NamedInterface("auth")
public final class InvalidTokenResponse {

    private InvalidTokenResponse() {
    }

    public static void write(final HttpServletResponse response) throws IOException {
        final ErrorCode errorCode = ErrorCode.INVALID_TOKEN;
        response.setStatus(errorCode.getStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"" + errorCode.name() + "\",\"message\":\"" + errorCode.getMessage() + "\"}"
        );
    }
}
