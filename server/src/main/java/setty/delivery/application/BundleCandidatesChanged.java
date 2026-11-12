package setty.delivery.application;

/** 한 배송원의 묶음 후보가 달라졌음을 그 배송원 SSE 연결에만 알리는 배송 모듈 내부 이벤트다. */
public record BundleCandidatesChanged(long driverId) {
}
