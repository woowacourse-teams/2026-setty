package setty.prototype.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import setty.prototype.web.NormalizedPhoneNumberDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 로그인 요청이 가입도 겸한다. 처음 보는 번호면 이 요청의 비밀번호로 회원을 만든다.
 * 그래서 비밀번호 규칙을 로그인 요청에서 확인한다.
 * 프로토타입은 입력 편의를 위해 비밀번호를 숫자 4자리로 받는다.
 */
public record LoginRequest(
        @JsonDeserialize(using = NormalizedPhoneNumberDeserializer.class)
        @NotBlank
        @Pattern(regexp = "^\\d{10,11}$")
        String phoneNumber,

        @NotBlank
        @Pattern(regexp = "^\\d{4}$")
        String password
) {
}
