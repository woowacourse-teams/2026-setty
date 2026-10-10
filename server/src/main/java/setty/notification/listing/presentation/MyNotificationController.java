package setty.notification.listing.presentation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.notification.listing.application.ListingNotificationService;
import setty.notification.listing.domain.ListingNotification;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.presentation.ListingListResponse;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.member.domain.Member;

@RestController
@RequestMapping("/api/me/notifications")
public class MyNotificationController {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final ListingNotificationService listingNotificationService;
    private final ListingRepository listingRepository;

    public MyNotificationController(
            final ListingNotificationService listingNotificationService,
            final ListingRepository listingRepository
    ) {
        this.listingNotificationService = listingNotificationService;
        this.listingRepository = listingRepository;
    }

    @GetMapping
    public ResponseEntity<ListingListResponse<NotificationResponse>> findMine(
            @LoginMember final Member member,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        final List<ListingNotification> notifications = listingNotificationService.findMine(
                member.getId(), Math.max(page, 0), Math.clamp(size, 1, MAXIMUM_PAGE_SIZE));
        final Map<Long, String> titles = findTitles(notifications);
        final List<NotificationResponse> items = notifications.stream()
                .map(notification -> NotificationResponse.of(notification, titles.get(notification.getListingId())))
                .toList();
        return ResponseEntity.ok(new ListingListResponse<>(items));
    }

    private Map<Long, String> findTitles(final List<ListingNotification> notifications) {
        final List<Long> listingIds = notifications.stream().map(ListingNotification::getListingId).toList();
        return listingRepository.findAllById(listingIds).stream()
                .collect(Collectors.toMap(Listing::getId, Listing::getTitle));
    }
}
