package setty.delivery.auth.api.dto;

import setty.delivery.auth.application.LoginResult;

public record LoginResponse(String token) {

    public static LoginResponse from(final LoginResult result) {
        return new LoginResponse(result.token());
    }
}
