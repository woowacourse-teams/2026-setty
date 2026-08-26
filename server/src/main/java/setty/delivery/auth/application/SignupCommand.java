package setty.delivery.auth.application;

public record SignupCommand(
        String loginId,
        String password,
        String phoneNumber,
        String licensePlateNumber,
        String carType,
        String businessRegistrationNumber
) {
}
