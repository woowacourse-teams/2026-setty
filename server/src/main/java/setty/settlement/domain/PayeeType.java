package setty.settlement.domain;

// 판매자는 members.id, 기사는 delivery_member.id로 ID 체계가 달라 받는 사람 ID와 함께 저장한다.
public enum PayeeType {
    SELLER,
    DRIVER
}
