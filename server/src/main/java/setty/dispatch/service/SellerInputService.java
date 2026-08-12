package setty.dispatch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.dispatch.domain.DispatchRequest;
import setty.dispatch.domain.SellerInputSession;
import setty.dispatch.dto.seller.SellerInputSessionResponse;
import setty.dispatch.dto.seller.SellerInputSubmitRequest;
import setty.dispatch.event.SellerInputCompletedEvent;
import setty.dispatch.exception.SellerInputSessionNotFoundException;
import setty.dispatch.repository.SellerInputSessionRepository;

@Service
public class SellerInputService {
    private static final Logger log = LoggerFactory.getLogger(SellerInputService.class);

    private final SellerInputSessionRepository sellerInputSessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SellerInputService(
            final SellerInputSessionRepository sellerInputSessionRepository,
            final ApplicationEventPublisher eventPublisher
    ) {
        this.sellerInputSessionRepository = sellerInputSessionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public SellerInputSessionResponse findSession(final String token) {
        return SellerInputSessionResponse.from(getSession(token));
    }

    @Transactional
    public void submit(final String token, final SellerInputSubmitRequest request) {
        final SellerInputSession session = getSession(token);
        session.complete(request.toSellerInput());
        final DispatchRequest dispatchRequest = session.getDispatchRequest();
        if (request.consented()) {
            dispatchRequest.recordSellerPrivacyConsent(request.privacyPolicyVersion());
        }

        log.info(
                "판매자 입력 완료. dispatchRequestId={}, sessionId={}, sessionStatus={}, status={}",
                dispatchRequest.getId(),
                session.getId(),
                session.getStatus(),
                dispatchRequest.getStatus()
        );

        eventPublisher.publishEvent(SellerInputCompletedEvent.from(dispatchRequest));
    }

    private SellerInputSession getSession(final String token) {
        return sellerInputSessionRepository.findByToken(token)
                .orElseThrow(SellerInputSessionNotFoundException::new);
    }
}
