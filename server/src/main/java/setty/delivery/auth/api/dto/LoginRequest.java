package setty.delivery.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import setty.delivery.auth.application.LoginCommand;

public record LoginRequest(
        @NotBlank String loginId,
        @NotBlank String password
) {

    public LoginCommand toCommand() {
        return new LoginCommand(loginId, password);
    }
}
