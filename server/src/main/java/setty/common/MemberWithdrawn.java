package setty.common;

/** 회원이 탈퇴한 사실. 알림 모듈이 그 회원의 키워드 구독·기기 토큰·알림을 지우는 데 사용한다. */
public record MemberWithdrawn(Long memberId) {
}
