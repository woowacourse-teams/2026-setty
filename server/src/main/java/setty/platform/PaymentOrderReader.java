package setty.platform;

/** 결제 모듈에 제공하는 주문 조회 경계. 구매자 불일치는 존재하지 않는 주문으로 응답한다. */
public interface PaymentOrderReader {

    void verifyBuyer(Long orderId, Long buyerId);

    int payableAmount(Long orderId);
}
