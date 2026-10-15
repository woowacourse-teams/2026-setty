package setty.platform;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;

@Service
public class NotificationListingReader {

    private final ListingRepository listingRepository;

    public NotificationListingReader(final ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    // 지난 알림도 제목을 보여 줘야 해서 삭제된 매물을 거르지 않는다.
    @Transactional(readOnly = true)
    public Map<Long, String> findTitles(final List<Long> listingIds) {
        return listingRepository.findAllById(listingIds).stream()
                .collect(Collectors.toMap(Listing::getId, Listing::getTitle));
    }
}
