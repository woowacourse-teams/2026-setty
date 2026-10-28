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
import setty.notification.listing.application.ListingToNotify;
import setty.notification.listing.application.NotificationDeliveryFee;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.presentation.ListResponse;
import setty.platform.MemberDeliveryArea;
import setty.platform.NotificationListingReader;
import setty.platform.NotificationMemberReader;
import setty.platform.NotifiedListing;

@RestController
@RequestMapping("/api/me/notifications")
public class MyNotificationController {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final ListingNotificationService listingNotificationService;
    private final NotificationListingReader notificationListingReader;
    private final NotificationMemberReader notificationMemberReader;
    private final NotificationDeliveryFee notificationDeliveryFee;

    public MyNotificationController(
            final ListingNotificationService listingNotificationService,
            final NotificationListingReader notificationListingReader,
            final NotificationMemberReader notificationMemberReader,
            final NotificationDeliveryFee notificationDeliveryFee
    ) {
        this.listingNotificationService = listingNotificationService;
        this.notificationListingReader = notificationListingReader;
        this.notificationMemberReader = notificationMemberReader;
        this.notificationDeliveryFee = notificationDeliveryFee;
    }

    // 배송비는 보는 사람 위치로 그때그때 계산한다. 저장하지 않으므로 주소를 바꾸면 지난 알림도 새 주소 기준으로 보인다.
    @GetMapping
    public ResponseEntity<ListResponse<NotificationResponse>> findMine(
            @LoginMember final Long memberId,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        final List<ListingNotification> notifications = listingNotificationService.findMine(
                memberId, Math.max(page, 0), Math.clamp(size, 1, MAXIMUM_PAGE_SIZE));
        final Map<Long, NotifiedListing> listings = notificationListingReader.findAll(
                notifications.stream().map(ListingNotification::getListingId).toList());
        final MemberDeliveryArea receiver = notificationMemberReader.findDeliveryArea(memberId).orElse(null);
        final List<NotificationResponse> items = notifications.stream()
                .map(notification -> toResponse(notification, listings.get(notification.getListingId()), receiver))
                .toList();
        return ResponseEntity.ok(new ListResponse<>(items));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> countUnread(@LoginMember final Long memberId) {
        return ResponseEntity.ok(new UnreadCountResponse(listingNotificationService.countUnread(memberId)));
    }

    @PostMapping("/read")
    public ResponseEntity<Void> readAll(@LoginMember final Long memberId) {
        listingNotificationService.readAll(memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> read(
            @LoginMember final Long memberId,
            @PathVariable final Long notificationId
    ) {
        listingNotificationService.read(memberId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{notificationId}/ack")
    public ResponseEntity<Void> ack(
            @LoginMember final Long memberId,
            @PathVariable final Long notificationId
    ) {
        listingNotificationService.ack(memberId, notificationId);
        return ResponseEntity.noContent().build();
    }

    private NotificationResponse toResponse(
            final ListingNotification notification,
            final NotifiedListing listing,
            final MemberDeliveryArea receiver
    ) {
        if (listing == null) {
            return NotificationResponse.of(notification, null, null, null, null);
        }
        final int deliveryFee = notificationDeliveryFee.of(ListingToNotify.from(listing), receiver);
        return NotificationResponse.of(notification, listing.title(), listing.price(), deliveryFee, listing.tradeStatus());
    }
}
