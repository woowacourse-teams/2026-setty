package setty.prototype.service;

import setty.prototype.domain.Member;

/**
 * @param newMember 이번 로그인 요청으로 회원이 새로 만들어졌는지
 */
public record AuthenticatedMember(
        Long id,
        String phoneNumber,
        boolean newMember
) {
    public static AuthenticatedMember signedUp(final Member member) {
        return new AuthenticatedMember(member.getId(), member.getPhoneNumber(), true);
    }

    public static AuthenticatedMember loggedIn(final Member member) {
        return new AuthenticatedMember(member.getId(), member.getPhoneNumber(), false);
    }
}
