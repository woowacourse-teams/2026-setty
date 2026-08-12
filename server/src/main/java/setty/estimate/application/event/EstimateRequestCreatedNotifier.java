package setty.estimate.application.event;

import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.common.notification.DiscordWebhookClient;
import setty.estimate.application.OperatorEstimateUrlFactory;

@Component
@RequiredArgsConstructor
public class EstimateRequestCreatedNotifier {
    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DiscordWebhookClient discordWebhookClient;
    private final OperatorEstimateUrlFactory operatorEstimateUrlFactory;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyCreated(final EstimateRequestCreatedEvent event) {
        discordWebhookClient.send(message(event));
    }

    private String message(final EstimateRequestCreatedEvent event) {
        return """
                📝 새 견적 요청이 접수됐어요
                • 요청 번호: #%d
                • 물품: %s%s
                • 접수: %s
                • 게시물: %s
                • 운영자 화면: %s""".formatted(
                event.estimateRequestId(),
                event.itemType(),
                event.highValueItem() ? " (50만 원 초과)" : "",
                event.createdAt().format(CREATED_AT_FORMAT),
                productLink(event.productLink()),
                operatorEstimateUrlFactory.create(event.estimateRequestId())
        );
    }

    private String productLink(final String productLink) {
        if (productLink == null || productLink.isBlank()) {
            return "없음";
        }
        return productLink;
    }
}
