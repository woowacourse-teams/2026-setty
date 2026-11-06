package setty.notification.listing.presentation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import setty.notification.inbox.NotificationInbox;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.order.application.OrderNotificationService;
import setty.notification.order.domain.OrderNotification;
import setty.notification.presentation.ListResponse;
import setty.platform.MemberDeliveryArea;
import setty.platform.NotificationListingReader;
import setty.platform.NotificationMemberReader;
import setty.platform.NotifiedListing;

@RestController
@RequestMapping("/api/me/notifications")
public class MyNotificationController {

    private static final int MAXIMUM_PAGE_SIZE = 100;
    // 주문 알림을 아는 클라이언트만 받는다. 옛 앱은 주문 알림 id를 키워드 알림 길로 읽음 처리해 엉뚱한 알림을 바꾸기 때문이다.
    private static final String INCLUDE_ORDER = "ORDER";

    private final ListingNotificationService listingNotificationService;
    private final NotificationListingReader notificationListingReader;
    private final NotificationMemberReader notificationMemberReader;
    private final NotificationDeliveryFee notificationDeliveryFee;
    private final OrderNotificationService orderNotificationService;
    private final NotificationInbox notificationInbox;

    public MyNotificationController(
            final ListingNotificationService listingNotificationService,
            final NotificationListingReader notificationListingReader,
            final NotificationMemberReader notificationMemberReader,
            final NotificationDeliveryFee notificationDeliveryFee,
            final OrderNotificationService orderNotificationService,
            final NotificationInbox notificationInbox
    ) {
        this.listingNotificationService = listingNotificationService;
        this.notificationListingReader = notificationListingReader;
        this.notificationMemberReader = notificationMemberReader;
        this.notificationDeliveryFee = notificationDeliveryFee;
        this.orderNotificationService = orderNotificationService;
        this.notificationInbox = notificationInbox;
    }

    // 배송비는 보는 사람 위치로 그때그때 계산한다. 저장하지 않으므로 주소를 바꾸면 지난 알림도 새 주소 기준으로 보인다.
    @GetMapping
    public ResponseEntity<ListResponse<NotificationResponse>> findMine(
            @LoginMember final Long memberId,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(required = false) final String include
    ) {
        final int safePage = Math.max(page, 0);
        final int safeSize = Math.clamp(size, 1, MAXIMUM_PAGE_SIZE);
        if (!includesOrders(include)) {
            final List<ListingNotification> notifications = listingNotificationService.findMine(memberId, safePage, safeSize);
            return ResponseEntity.ok(new ListResponse<>(toKeywordResponses(memberId, notifications)));
        }
        return ResponseEntity.ok(new ListResponse<>(findMixed(memberId, safePage, safeSize)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> countUnread(
            @LoginMember final Long memberId,
            @RequestParam(required = false) final String include
    ) {
        final long orders = includesOrders(include) ? orderNotificationService.countUnread(memberId) : 0;
        return ResponseEntity.ok(new UnreadCountResponse(listingNotificationService.countUnread(memberId) + orders));
    }

    @PostMapping("/read")
    public ResponseEntity<Void> readAll(
            @LoginMember final Long memberId,
            @RequestParam(required = false) final String include
    ) {
        listingNotificationService.readAll(memberId);
        if (includesOrders(include)) {
            orderNotificationService.readAll(memberId);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/orders/{notificationId}/read")
    public ResponseEntity<Void> readOrder(
            @LoginMember final Long memberId,
            @PathVariable final Long notificationId
    ) {
        orderNotificationService.read(memberId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/orders/{notificationId}/ack")
    public ResponseEntity<Void> ackOrder(
            @LoginMember final Long memberId,
            @PathVariable final Long notificationId
    ) {
        orderNotificationService.ack(memberId, notificationId);
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

    private static boolean includesOrders(final String include) {
        return include != null && Stream.of(include.split(",")).map(String::trim).anyMatch(INCLUDE_ORDER::equalsIgnoreCase);
    }

    private List<NotificationResponse> toKeywordResponses(final Long memberId, final List<ListingNotification> notifications) {
        final Map<Long, NotifiedListing> listings = notificationListingReader.findAll(
                notifications.stream().map(ListingNotification::getListingId).toList());
        final MemberDeliveryArea receiver = notificationMemberReader.findDeliveryArea(memberId).orElse(null);
        return notifications.stream()
                .map(notification -> toResponse(notification, listings.get(notification.getListingId()), receiver))
                .toList();
    }

    private List<NotificationResponse> findMixed(final Long memberId, final int page, final int size) {
        final List<NotificationInbox.Entry> entries = notificationInbox.findPage(memberId, page, size);
        final Map<Long, NotificationResponse> keywords = toKeywordResponses(memberId,
                listingNotificationService.findAll(idsOf(entries, NotificationInbox.Type.KEYWORD))).stream()
                .collect(Collectors.toMap(NotificationResponse::id, Function.identity()));
        final List<OrderNotification> orders = orderNotificationService.findAll(idsOf(entries, NotificationInbox.Type.ORDER));
        final Map<Long, NotifiedListing> orderListings = notificationListingReader.findAll(
                orders.stream().map(OrderNotification::getListingId).distinct().toList());
        final Map<Long, NotificationResponse> orderResponses = orders.stream()
                .map(notification -> toResponse(notification, orderListings.get(notification.getListingId())))
                .collect(Collectors.toMap(NotificationResponse::id, Function.identity()));
        return entries.stream()
                .map(entry -> entry.type() == NotificationInbox.Type.KEYWORD
                        ? keywords.get(entry.id())
                        : orderResponses.get(entry.id()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<Long> idsOf(final List<NotificationInbox.Entry> entries, final NotificationInbox.Type type) {
        return entries.stream().filter(entry -> entry.type() == type).map(NotificationInbox.Entry::id).toList();
    }

    private static NotificationResponse toResponse(final OrderNotification notification, final NotifiedListing listing) {
        if (listing == null) {
            return NotificationResponse.of(notification, null, null);
        }
        return NotificationResponse.of(notification, listing.title(), listing.tradeStatus());
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
