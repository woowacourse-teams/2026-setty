package setty.dispatch.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(BuyerDispatchRequestService.class);

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
        final DispatchRequest newDispatchRequest = new DispatchRequest(
                UUID.randomUUID().toString(),
                request.buyerName(),
                PhoneNumbers.normalize(request.buyerPhoneNumber()),
                request.deliveryAddress(),
                request.itemType(),
                request.highValueItem(),
                request.productLink(),
                request.itemImageUrlsOrEmpty(),
                request.estimateRequestId()
        );
        if (request.consented()) {
            newDispatchRequest.recordBuyerPrivacyConsent(request.privacyPolicyVersion());
        }
        final DispatchRequest dispatchRequest = dispatchRequestRepository.save(newDispatchRequest);
        final SellerInputSession session = sellerInputSessionRepository.save(
                new SellerInputSession(UUID.randomUUID().toString(), dispatchRequest)
        );

        log.info(
                "배차 요청 접수 완료. dispatchRequestId={}, sessionId={}, sessionStatus={}",
                dispatchRequest.getId(),
                session.getId(),
                session.getStatus()
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

        log.info("구매자 최종 금액 동의 완료. dispatchRequestId={}, status={}",
                dispatchRequest.getId(), dispatchRequest.getStatus());
    }
}
