package setty.notification.member.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.common.MemberWithdrawn;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.keyword.application.KeywordSubscriptionService;
import setty.notification.listing.application.ListingNotificationService;
import setty.notification.order.application.OrderNotificationService;
import setty.notification.owner.NotificationOwner;

@Component
public class MemberWithdrawalListener {

    private final KeywordSubscriptionService keywordSubscriptionService;
    private final DeviceTokenService deviceTokenService;
    private final ListingNotificationService listingNotificationService;
    private final OrderNotificationService orderNotificationService;

    public MemberWithdrawalListener(
            final KeywordSubscriptionService keywordSubscriptionService,
            final DeviceTokenService deviceTokenService,
            final ListingNotificationService listingNotificationService,
            final OrderNotificationService orderNotificationService
    ) {
        this.keywordSubscriptionService = keywordSubscriptionService;
        this.deviceTokenService = deviceTokenService;
        this.listingNotificationService = listingNotificationService;
        this.orderNotificationService = orderNotificationService;
    }

    @ApplicationModuleListener
    public void onMemberWithdrawn(final MemberWithdrawn event) {
        final NotificationOwner member = NotificationOwner.member(event.memberId());
        keywordSubscriptionService.unsubscribeAll(event.memberId());
        deviceTokenService.unregisterAll(member);
        listingNotificationService.deleteAllOf(event.memberId());
        orderNotificationService.deleteAllOf(member);
    }
}
