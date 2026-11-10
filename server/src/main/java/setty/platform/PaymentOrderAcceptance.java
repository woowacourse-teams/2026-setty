package setty.platform;

import java.time.Instant;

/**
 * 결제 모듈에 제공하는 주문 결정 경계. 토스 승인을 확인한 뒤, 결제를 기록하기 전에 부른다.
 *
 * <p>주문이 승인을 받아들일지 정하고 반환 전에 커밋한다. 결제는 이 결과를 보고 기록하므로,
 * 승인을 기록한 결제의 주문이 결제 대기로 남지 않는다. 같은 주문으로 다시 불러도 같은 결과를 돌려준다.
 * 호출하는 쪽은 자기 트랜잭션 밖에서 부른다.
 *
 * <p>만료 판정은 처리 시각이 아니라 승인을 확인한 시각({@code confirmedAt})으로 한다(docs/policy/payment.md 승인·재시도).
 * 잠금을 기다리는 동안 기한이 지나도 결과가 바뀌지 않는다.
 */
public interface PaymentOrderAcceptance {

    PaymentAcceptance accept(Long orderId, Instant confirmedAt);
}
