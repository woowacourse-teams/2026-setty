package setty.platform.member.controller.dto;

import setty.platform.member.domain.Member;

public record MemberMeResponse(Long id, String loginId, String role, String phoneNumber, String address) {

    public static MemberMeResponse from(final Member member) {
        return new MemberMeResponse(
                member.getId(),
                member.getLoginId(),
                member.getRole().name(),
                member.getPhoneNumber(),
                member.getAddress()
        );
    }
}
