package setty.platform;

import java.util.Optional;

/** 배송 부재는 empty, 조회 장애는 예외다. 두 결과를 섞지 않는다. */
public interface DeliverySupportReader {
    Optional<State> findState(Long orderId);
    enum State { REQUESTED, ACCEPTED, PICKED_UP, DELIVERED, CANCELLED }
}
