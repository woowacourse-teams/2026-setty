package setty.notification.listing.application;

import org.springframework.stereotype.Component;
import setty.deliveryfee.DeliveryFeeService;
import setty.platform.MemberDeliveryArea;

@Component
public class NotificationDeliveryFee {

    private final DeliveryFeeService deliveryFeeService;

    public NotificationDeliveryFee(final DeliveryFeeService deliveryFeeService) {
        this.deliveryFeeService = deliveryFeeService;
    }

    public int of(final ListingToNotify listing, final MemberDeliveryArea receiver) {
        return deliveryFeeService.feeOrDefault(
                listing.sellerDongCode(),
                listing.sellerZipZone(),
                receiver == null ? null : receiver.dongCode(),
                receiver == null ? null : receiver.zipCode(),
                listing.volumeTier(),
                listing.fixedDeliveryFee()
        );
    }
}
