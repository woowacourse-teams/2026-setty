package setty.platform.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Entity
@Table(name = "listing_images")
public class ListingImage {

    private static final int MAXIMUM_OBJECT_KEY_LENGTH = 1_024;
    private static final int MINIMUM_DISPLAY_ORDER = 1;
    private static final int MAXIMUM_DISPLAY_ORDER = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "object_key", nullable = false, length = MAXIMUM_OBJECT_KEY_LENGTH)
    private String objectKey;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    protected ListingImage() {
    }

    private ListingImage(final Long listingId, final String objectKey, final Integer displayOrder) {
        validateListingId(listingId);
        validateObjectKey(objectKey);
        validateDisplayOrder(displayOrder);
        this.listingId = listingId;
        this.objectKey = objectKey.trim();
        this.displayOrder = displayOrder;
    }

    public static ListingImage create(
            final Long listingId,
            final String objectKey,
            final Integer displayOrder
    ) {
        return new ListingImage(listingId, objectKey, displayOrder);
    }

    public void changeDisplayOrder(final Integer displayOrder) {
        validateDisplayOrder(displayOrder);
        this.displayOrder = displayOrder;
    }

    public boolean belongsTo(final Long listingId) {
        return this.listingId.equals(listingId);
    }

    private static void validateListingId(final Long listingId) {
        if (listingId == null || listingId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static void validateObjectKey(final String objectKey) {
        if (objectKey == null
                || objectKey.trim().isEmpty()
                || objectKey.trim().length() > MAXIMUM_OBJECT_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static void validateDisplayOrder(final Integer displayOrder) {
        if (displayOrder == null
                || displayOrder < MINIMUM_DISPLAY_ORDER
                || displayOrder > MAXIMUM_DISPLAY_ORDER) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getListingId() {
        return listingId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
