package setty.notification.listing.application;

import setty.common.ListingCreated;
import setty.deliveryfee.VolumeTier;
import setty.platform.NotifiedListing;

public record ListingToNotify(
        Long listingId,
        Long sellerId,
        String title,
        String category,
        int price,
        String sellerDongCode,
        Integer sellerZipZone,
        VolumeTier volumeTier,
        int fixedDeliveryFee
) {

    public static ListingToNotify from(final ListingCreated event) {
        return new ListingToNotify(
                event.listingId(),
                event.sellerId(),
                event.title(),
                event.category(),
                event.price(),
                event.sellerDongCode(),
                event.sellerZipZone(),
                event.volumeTier(),
                event.fixedDeliveryFee()
        );
    }

    public static ListingToNotify from(final NotifiedListing listing) {
        return new ListingToNotify(
                listing.id(),
                null,
                listing.title(),
                null,
                listing.price(),
                listing.dongCode(),
                listing.zipZone(),
                listing.volumeTier(),
                listing.fixedDeliveryFee()
        );
    }
}
