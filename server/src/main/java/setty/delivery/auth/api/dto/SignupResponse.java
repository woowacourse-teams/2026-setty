package setty.delivery.auth.api.dto;

import setty.delivery.auth.application.SignupResult;

public record SignupResponse(Long id, String loginId) {

    public static SignupResponse from(final SignupResult result) {
        return new SignupResponse(result.id(), result.loginId());
    }
}
