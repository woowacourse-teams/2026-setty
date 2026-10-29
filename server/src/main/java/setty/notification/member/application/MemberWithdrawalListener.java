package setty.notification.member.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.common.MemberWithdrawn;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.keyword.application.KeywordSubscriptionService;
import setty.notification.listing.application.ListingNotificationService;

@Component
public class MemberWithdrawalListener {

    private final KeywordSubscriptionService keywordSubscriptionService;
    private final DeviceTokenService deviceTokenService;
    private final ListingNotificationService listingNotificationService;

    public MemberWithdrawalListener(
            final KeywordSubscriptionService keywordSubscriptionService,
            final DeviceTokenService deviceTokenService,
            final ListingNotificationService listingNotificationService
    ) {
        this.keywordSubscriptionService = keywordSubscriptionService;
        this.deviceTokenService = deviceTokenService;
        this.listingNotificationService = listingNotificationService;
    }

    @ApplicationModuleListener
    public void onMemberWithdrawn(final MemberWithdrawn event) {
        keywordSubscriptionService.unsubscribeAll(event.memberId());
        deviceTokenService.unregisterAll(event.memberId());
        listingNotificationService.deleteAllOf(event.memberId());
    }
}
