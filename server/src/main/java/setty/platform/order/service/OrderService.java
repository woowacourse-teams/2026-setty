package setty.platform.order.service;

import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.OrderRequested;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.member.domain.Member;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(final OrderRepository orderRepository, final ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order create(final OrderCreateRequest request, final Member buyer) {
        if (orderRepository.existsByListingId(request.listingId())) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }

        final Order order;
        try {
            order = orderRepository.saveAndFlush(new Order(request.listingId(), buyer.getId()));
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }

        eventPublisher.publishEvent(new OrderRequested(
                order.getId(),
                "",
                "",
                "",
                buyer.getAddress(),
                0,
                "",
                buyer.getPhoneNumber()
        ));

        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> findMyOrders(final Long buyerId) {
        return orderRepository.findAllByBuyerIdOrderByIdDesc(buyerId);
    }

    @Transactional(readOnly = true)
    public Order findMyOrder(final Long orderId, final Long buyerId) {
        return orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
