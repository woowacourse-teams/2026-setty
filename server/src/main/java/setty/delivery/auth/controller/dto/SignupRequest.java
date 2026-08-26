package setty.delivery.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]{4,20}$", message = "아이디는 영문 소문자·숫자 4~20자입니다")
        String loginId,

        @NotBlank
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자입니다")
        String password,

        @NotBlank
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호는 010-0000-0000 형식입니다")
        String phoneNumber,

        @NotBlank
        @Pattern(regexp = "^\\d{2,3}[가-힣]\\d{4}$", message = "차량 번호판은 00가0000 형식입니다")
        String licensePlateNumber,

        @NotBlank
        @Size(max = 30, message = "차종은 30자 이내입니다")
        String carType,

        @NotBlank
        @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$", message = "사업자등록번호는 000-00-00000 형식입니다")
        String businessRegistrationNumber
) {
}
