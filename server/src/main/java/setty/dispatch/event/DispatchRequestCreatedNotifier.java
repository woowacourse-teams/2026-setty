package setty.dispatch.event;

import java.time.format.DateTimeFormatter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.common.notification.DiscordWebhookClient;
import setty.dispatch.service.OperatorDispatchUrlFactory;

@Component
public class DispatchRequestCreatedNotifier {
    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DiscordWebhookClient discordWebhookClient;
    private final OperatorDispatchUrlFactory operatorDispatchUrlFactory;

    public DispatchRequestCreatedNotifier(
            final DiscordWebhookClient discordWebhookClient,
            final OperatorDispatchUrlFactory operatorDispatchUrlFactory
    ) {
        this.discordWebhookClient = discordWebhookClient;
        this.operatorDispatchUrlFactory = operatorDispatchUrlFactory;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyCreated(final DispatchRequestCreatedEvent event) {
        discordWebhookClient.send(message(event));
    }

    private String message(final DispatchRequestCreatedEvent event) {
        return """
                🚚 새 배차 요청이 접수됐어요
                • 요청 번호: #%d
                • 물품: %s%s
                • 물품 사진: %s
                • 접수: %s
                • 게시물: %s
                • 운영자 화면: %s""".formatted(
                event.dispatchRequestId(),
                event.itemType(),
                event.highValueItem() ? " (50만 원 초과)" : "",
                itemImages(event.itemImageCount()),
                event.createdAt().format(CREATED_AT_FORMAT),
                productLink(event.productLink()),
                operatorDispatchUrlFactory.create(event.dispatchRequestId())
        );
    }

    private String itemImages(final int itemImageCount) {
        if (itemImageCount == 0) {
            return "없음";
        }
        return itemImageCount + "장";
    }

    private String productLink(final String productLink) {
        if (productLink == null || productLink.isBlank()) {
            return "없음";
        }
        return productLink;
    }
}
