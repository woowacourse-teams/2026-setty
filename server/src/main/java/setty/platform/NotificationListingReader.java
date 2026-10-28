package setty.platform;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.platform.listing.domain.DeliveryFeePolicy;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.SaleStatus;
import setty.platform.listing.repository.ListingRepository;

@Service
public class NotificationListingReader {

    private final ListingRepository listingRepository;

    public NotificationListingReader(final ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    // 지난 알림도 보여 줘야 해서 삭제된 매물을 거르지 않는다.
    @Transactional(readOnly = true)
    public Map<Long, NotifiedListing> findAll(final List<Long> listingIds) {
        return listingRepository.findAllById(listingIds).stream()
                .map(NotificationListingReader::toNotified)
                .collect(Collectors.toMap(NotifiedListing::id, listing -> listing));
    }

    private static NotifiedListing toNotified(final Listing listing) {
        return new NotifiedListing(
                listing.getId(),
                listing.getTitle(),
                listing.getPrice(),
                listing.getDongCode(),
                listing.getZipZone(),
                DeliveryFeePolicy.volumeTier(listing.getDimensions()),
                listing.getDeliveryFee(),
                tradeStatusOf(listing)
        );
    }

    private static String tradeStatusOf(final Listing listing) {
        if (listing.isDeleted()) {
            return "DELETED";
        }
        if (listing.getSaleStatus() == SaleStatus.SOLD) {
            return "SOLD";
        }
        if (listing.getSaleStatus() == SaleStatus.RESERVED || listing.hasPurchaseRequest()) {
            return "TRADING";
        }
        return "AVAILABLE";
    }
}
