package setty.platform.member.domain;

// 가입은 항상 MEMBER. ADMIN은 가입 API로 만들 수 없고 DB에서 직접 지정한다.
// 기사(배송원)는 members가 아니라 배송 팀의 delivery_member 계정으로 관리한다 (#190, #194).
public enum MemberRole {
    MEMBER,
    ADMIN
}
