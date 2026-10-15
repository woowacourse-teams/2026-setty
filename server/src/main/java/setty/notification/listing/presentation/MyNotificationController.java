package setty.notification.listing.presentation;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.notification.listing.application.ListingNotificationService;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.presentation.ListResponse;
import setty.platform.NotificationListingReader;

@RestController
@RequestMapping("/api/me/notifications")
public class MyNotificationController {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final ListingNotificationService listingNotificationService;
    private final NotificationListingReader notificationListingReader;

    public MyNotificationController(
            final ListingNotificationService listingNotificationService,
            final NotificationListingReader notificationListingReader
    ) {
        this.listingNotificationService = listingNotificationService;
        this.notificationListingReader = notificationListingReader;
    }

    @GetMapping
    public ResponseEntity<ListResponse<NotificationResponse>> findMine(
            @LoginMember final Long memberId,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        final List<ListingNotification> notifications = listingNotificationService.findMine(
                memberId, Math.max(page, 0), Math.clamp(size, 1, MAXIMUM_PAGE_SIZE));
        final Map<Long, String> titles = notificationListingReader.findTitles(
                notifications.stream().map(ListingNotification::getListingId).toList());
        final List<NotificationResponse> items = notifications.stream()
                .map(notification -> NotificationResponse.of(notification, titles.get(notification.getListingId())))
                .toList();
        return ResponseEntity.ok(new ListResponse<>(items));
    }

    @PostMapping("/{notificationId}/ack")
    public ResponseEntity<Void> ack(
            @LoginMember final Long memberId,
            @PathVariable final Long notificationId
    ) {
        listingNotificationService.ack(memberId, notificationId);
        return ResponseEntity.noContent().build();
    }
}
