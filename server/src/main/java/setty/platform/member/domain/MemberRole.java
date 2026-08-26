package setty.platform.member.domain;

// 가입은 항상 MEMBER. ADMIN은 가입 API로 만들 수 없고 DB에서 직접 지정한다.
// DRIVER는 배송 팀이 DeliveryController 인증을 @LoginDeliveryMember로 전환하면 제거 예정 — 신규 발급은 이미 불가.
public enum MemberRole {
    MEMBER,
    ADMIN,
    DRIVER
}
