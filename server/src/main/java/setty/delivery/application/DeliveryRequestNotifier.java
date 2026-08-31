package setty.delivery.application;

/**
 * 기사 앱에 배송 요청 목록 재조회가 필요함을 알린다.
 */
public interface DeliveryRequestNotifier {

    void notifyRequestsChanged();
}
