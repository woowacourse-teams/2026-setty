package setty.platform.member.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호는 010-0000-0000 형식입니다")
        String phoneNumber,

        @NotBlank
        @Size(max = 200, message = "주소는 200자 이내입니다")
        String address
) {
}
