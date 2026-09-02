package setty.platform.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Entity
@Table(name = "listings")
public class Listing {

    private static final int MAXIMUM_TITLE_LENGTH = 100;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 1_000;
    private static final int MAXIMUM_PRICE = 100_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = MAXIMUM_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, length = MAXIMUM_DESCRIPTION_LENGTH)
    private String description;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "delivery_fee", nullable = false)
    private Integer deliveryFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_grade", nullable = false, length = 1)
    private ConditionGrade conditionGrade;

    @Embedded
    private Dimensions dimensions;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_status", nullable = false, length = 20)
    private SaleStatus saleStatus;

    @Column(name = "has_purchase_request", nullable = false)
    private boolean hasPurchaseRequest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Listing() {
    }

    private Listing(
            final Long sellerId,
            final String title,
            final String description,
            final Integer price,
            final ListingCategory category,
            final ConditionGrade conditionGrade,
            final Dimensions dimensions
    ) {
        validateSellerId(sellerId);
        validateFields(title, description, price, category, conditionGrade, dimensions);
        final Instant now = Instant.now();
        this.sellerId = sellerId;
        this.title = title.trim();
        this.description = description.trim();
        this.price = price;
        this.deliveryFee = DeliveryFeePolicy.calculate(dimensions);
        this.category = category;
        this.conditionGrade = conditionGrade;
        this.dimensions = dimensions;
        this.saleStatus = SaleStatus.AVAILABLE;
        this.hasPurchaseRequest = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Listing create(
            final Long sellerId,
            final String title,
            final String description,
            final Integer price,
            final ListingCategory category,
            final ConditionGrade conditionGrade,
            final Dimensions dimensions
    ) {
        return new Listing(sellerId, title, description, price, category, conditionGrade, dimensions);
    }

    public void update(
            final String title,
            final String description,
            final Integer price,
            final ListingCategory category,
            final ConditionGrade conditionGrade,
            final Dimensions dimensions
    ) {
        ensureUpdatable();
        validateFields(title, description, price, category, conditionGrade, dimensions);
        this.title = title.trim();
        this.description = description.trim();
        this.price = price;
        this.deliveryFee = DeliveryFeePolicy.calculate(dimensions);
        this.category = category;
        this.conditionGrade = conditionGrade;
        this.dimensions = dimensions;
        touch();
    }

    public void registerPurchaseRequest() {
        if (isDeleted() || saleStatus != SaleStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.LISTING_NOT_AVAILABLE);
        }
        if (hasPurchaseRequest) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
        this.hasPurchaseRequest = true;
        touch();
    }

    // 결제 실패로 주문이 취소될 때 선점을 되돌린다. 멱등 — 이미 해제돼 있어도 예외 없이 통과한다.
    public void releasePurchaseRequest() {
        releasePurchaseRequestState();
    }

    public void releasePurchaseRequestForExpiredPendingOrder() {
        releasePurchaseRequestState();
    }

    private void releasePurchaseRequestState() {
        if (!hasPurchaseRequest) {
            return;
        }
        this.hasPurchaseRequest = false;
        touch();
    }

    public boolean reserve() {
        if (isDeleted()) {
            return false;
        }
        if (saleStatus == SaleStatus.RESERVED || saleStatus == SaleStatus.SOLD) {
            return false;
        }
        if (!hasPurchaseRequest) {
            throw new BusinessException(ErrorCode.INVALID_LISTING_STATUS_TRANSITION);
        }
        this.saleStatus = SaleStatus.RESERVED;
        touch();
        return true;
    }

    public void completeSale() {
        if (saleStatus == SaleStatus.SOLD) {
            return;
        }
        if (isDeleted() || saleStatus != SaleStatus.RESERVED) {
            throw new BusinessException(ErrorCode.INVALID_LISTING_STATUS_TRANSITION);
        }
        this.saleStatus = SaleStatus.SOLD;
        touch();
    }

    public void softDelete() {
        if (!canDelete()) {
            throw new BusinessException(ErrorCode.LISTING_DELETE_NOT_ALLOWED);
        }
        final Instant now = Instant.now();
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(final Long memberId) {
        return sellerId.equals(memberId);
    }

    public boolean canUpdate() {
        return !isDeleted() && saleStatus == SaleStatus.AVAILABLE && !hasPurchaseRequest;
    }

    public boolean canDelete() {
        return canUpdate();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public int getTotalPrice() {
        return price + deliveryFee;
    }

    private void ensureUpdatable() {
        if (!canUpdate()) {
            throw new BusinessException(ErrorCode.LISTING_UPDATE_NOT_ALLOWED);
        }
    }

    private static void validateSellerId(final Long sellerId) {
        if (sellerId == null || sellerId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static void validateFields(
            final String title,
            final String description,
            final Integer price,
            final ListingCategory category,
            final ConditionGrade conditionGrade,
            final Dimensions dimensions
    ) {
        if (isBlankOrTooLong(title, MAXIMUM_TITLE_LENGTH)
                || isBlankOrTooLong(description, MAXIMUM_DESCRIPTION_LENGTH)
                || price == null
                || price < 0
                || price > MAXIMUM_PRICE
                || category == null
                || conditionGrade == null
                || dimensions == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static boolean isBlankOrTooLong(final String value, final int maximumLength) {
        return value == null || value.trim().isEmpty() || value.trim().length() > maximumLength;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    @PrePersist
    private void initializeTimestamps() {
        final Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getPrice() {
        return price;
    }

    public Integer getDeliveryFee() {
        return deliveryFee;
    }

    public ListingCategory getCategory() {
        return category;
    }

    public ConditionGrade getConditionGrade() {
        return conditionGrade;
    }

    public Dimensions getDimensions() {
        return dimensions;
    }

    public SaleStatus getSaleStatus() {
        return saleStatus;
    }

    public boolean hasPurchaseRequest() {
        return hasPurchaseRequest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
