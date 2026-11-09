package setty.platform.order.service;

import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import setty.common.OrderStatusNotified;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.order.domain.Order;

@Component
public class OrderStatusNotifier {

    private final ListingRepository listingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderStatusNotifier(final ListingRepository listingRepository, final ApplicationEventPublisher eventPublisher) {
        this.listingRepository = listingRepository;
        this.eventPublisher = eventPublisher;
    }

    // 상태가 실제로 바뀐 트랜잭션 안에서만 부른다. 같은 원인 이벤트가 다시 와서 상태가 그대로면 부르지 않는다.
    public void notify(final Order order, final OrderStatusNotified.Change change, final Instant occurredAt) {
        final Listing listing = listingRepository.findById(order.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        eventPublisher.publishEvent(new OrderStatusNotified(
                order.getId(),
                listing.getId(),
                order.getBuyerId(),
                listing.getSellerId(),
                listing.getTitle(),
                change,
                occurredAt
        ));
    }
}
