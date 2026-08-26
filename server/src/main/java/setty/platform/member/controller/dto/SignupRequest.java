package setty.platform.member.controller.dto;

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
        @Size(max = 200, message = "주소는 200자 이내입니다")
        String address,

        @NotBlank
        @Pattern(regexp = "^(PLATFORM|DRIVER)$", message = "role은 PLATFORM 또는 DRIVER입니다")
        String role
) {
}
