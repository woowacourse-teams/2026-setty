package setty.delivery.auth.application;

public record LoginCommand(
        String loginId,
        String password
) {
}
