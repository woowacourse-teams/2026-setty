package setty.platform.member.controller.dto;

import setty.platform.member.domain.Member;

public record SignupResponse(Long id, String loginId, String role) {

    public static SignupResponse from(final Member member) {
        return new SignupResponse(member.getId(), member.getLoginId(), member.getRole().name());
    }
}
