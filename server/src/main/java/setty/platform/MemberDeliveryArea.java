package setty.platform;

/** 알림 모듈에 제공하는 회원의 배송 위치. 동과 우편번호는 서울 밖이거나 주소 해석 전이면 비어 있다. */
public record MemberDeliveryArea(Long memberId, String dongCode, String zipCode) {
}
