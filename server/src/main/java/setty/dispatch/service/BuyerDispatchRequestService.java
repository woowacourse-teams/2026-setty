package setty.dispatch.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.phone.PhoneNumbers;
import setty.dispatch.domain.DispatchRequest;
import setty.dispatch.domain.DispatchStatus;
import setty.dispatch.domain.SellerInputSession;
import setty.dispatch.dto.buyer.BuyerDispatchRequestCreateRequest;
import setty.dispatch.dto.buyer.BuyerDispatchRequestCreateResponse;
import setty.dispatch.dto.buyer.BuyerDispatchRequestResponse;
import setty.dispatch.exception.DispatchRequestNotFoundException;
import setty.dispatch.repository.DispatchRequestRepository;
import setty.dispatch.repository.SellerInputSessionRepository;

@Service
public class BuyerDispatchRequestService {
    private final DispatchRequestRepository dispatchRequestRepository;
    private final SellerInputSessionRepository sellerInputSessionRepository;
    private final SellerInputUrlFactory sellerInputUrlFactory;

    public BuyerDispatchRequestService(
            final DispatchRequestRepository dispatchRequestRepository,
            final SellerInputSessionRepository sellerInputSessionRepository,
            final SellerInputUrlFactory sellerInputUrlFactory
    ) {
        this.dispatchRequestRepository = dispatchRequestRepository;
        this.sellerInputSessionRepository = sellerInputSessionRepository;
        this.sellerInputUrlFactory = sellerInputUrlFactory;
    }

    @Transactional
    public BuyerDispatchRequestCreateResponse create(final BuyerDispatchRequestCreateRequest request) {
        final DispatchRequest dispatchRequest = dispatchRequestRepository.save(new DispatchRequest(
                UUID.randomUUID().toString(),
                request.buyerName(),
                PhoneNumbers.normalize(request.buyerPhoneNumber()),
                request.deliveryAddress(),
                request.itemType(),
                request.highValueItem(),
                request.productLink(),
                request.itemImageUrlsOrEmpty(),
                request.estimateRequestId()
        ));
        final SellerInputSession session = sellerInputSessionRepository.save(
                new SellerInputSession(UUID.randomUUID().toString(), dispatchRequest)
        );

        return new BuyerDispatchRequestCreateResponse(
                dispatchRequest.getBuyerToken(),
                sellerInputUrlFactory.create(session.getToken())
        );
    }

    @Transactional(readOnly = true)
    public BuyerDispatchRequestResponse findByBuyerToken(final String buyerToken) {
        final DispatchRequest dispatchRequest = dispatchRequestRepository.findByBuyerToken(buyerToken)
                .orElseThrow(DispatchRequestNotFoundException::new);
        final String sellerInputUrl = sellerInputSessionRepository.findByDispatchRequestId(dispatchRequest.getId())
                .map(SellerInputSession::getToken)
                .map(sellerInputUrlFactory::create)
                .orElse(null);

        return BuyerDispatchRequestResponse.from(dispatchRequest, sellerInputUrl);
    }

    @Transactional
    public void approveFinalAmount(final String buyerToken) {
        final DispatchRequest dispatchRequest = dispatchRequestRepository.findByBuyerToken(buyerToken)
                .orElseThrow(DispatchRequestNotFoundException::new);

        if (dispatchRequest.getStatus() == DispatchStatus.DISPATCH_PENDING) {
            return;
        }
        dispatchRequest.approveFinalAmount();
    }
}
