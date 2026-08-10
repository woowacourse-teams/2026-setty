package setty.dispatch.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import setty.common.time.SeoulDateTime;
import setty.dispatch.exception.DispatchStatusTransitionException;

@Entity
public class DispatchRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String buyerToken;

    @Column(nullable = false)
    private String buyerName;

    @Column(nullable = false)
    private String buyerPhoneNumber;

    @Column(nullable = false)
    private String deliveryAddress;

    @Column(nullable = false)
    private String itemType;

    @Column(nullable = false)
    private boolean highValueItem;

    @Column(length = 500)
    private String productLink;

    @ElementCollection
    @CollectionTable(name = "dispatch_request_item_images", joinColumns = @JoinColumn(name = "dispatch_request_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", length = 500)
    private List<String> itemImageUrls = new ArrayList<>();

    private Long estimateRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DispatchStatus status;

    @Embedded
    private SellerInput sellerInput;

    private LocalDateTime sellerInputCompletedAt;

    private Integer finalQuotedAmount;

    @Column(columnDefinition = "TEXT")
    private String messageContent;

    private LocalDateTime amountCheckedAt;

    @Column(length = 500)
    private String operatorNote;

    @Column(length = 200)
    private String closedReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected DispatchRequest() {
    }

    public DispatchRequest(
            final String buyerToken,
            final String buyerName,
            final String buyerPhoneNumber,
            final String deliveryAddress,
            final String itemType,
            final boolean highValueItem,
            final String productLink,
            final List<String> itemImageUrls,
            final Long estimateRequestId
    ) {
        this.buyerToken = buyerToken;
        this.buyerName = buyerName;
        this.buyerPhoneNumber = buyerPhoneNumber;
        this.deliveryAddress = deliveryAddress;
        this.itemType = itemType;
        this.highValueItem = highValueItem;
        this.productLink = productLink;
        this.itemImageUrls = new ArrayList<>(itemImageUrls);
        this.estimateRequestId = estimateRequestId;
        this.status = DispatchStatus.SELLER_INPUT_PENDING;
        this.createdAt = SeoulDateTime.now();
    }

    public void completeSellerInput(final SellerInput input) {
        if (status != DispatchStatus.SELLER_INPUT_PENDING) {
            throw new DispatchStatusTransitionException(status, DispatchStatus.FINAL_REVIEW_PENDING);
        }
        this.sellerInput = input;
        this.sellerInputCompletedAt = SeoulDateTime.now();
        this.status = DispatchStatus.FINAL_REVIEW_PENDING;
    }

    public boolean isSellerInputCompleted() {
        return sellerInput != null && sellerInput.isPresent();
    }

    public void recordFinalAmount(final int amount) {
        if (status != DispatchStatus.FINAL_REVIEW_PENDING
                && status != DispatchStatus.FINAL_AMOUNT_CONFIRM_PENDING) {
            throw new DispatchStatusTransitionException(status, DispatchStatus.FINAL_AMOUNT_CONFIRM_PENDING);
        }
        this.finalQuotedAmount = amount;
        this.status = DispatchStatus.FINAL_AMOUNT_CONFIRM_PENDING;
    }

    public void approveFinalAmount() {
        if (status != DispatchStatus.FINAL_AMOUNT_CONFIRM_PENDING) {
            throw new DispatchStatusTransitionException(status, DispatchStatus.DISPATCH_PENDING);
        }
        this.amountCheckedAt = SeoulDateTime.now();
        this.status = DispatchStatus.DISPATCH_PENDING;
    }

    public void completeDispatch() {
        if (status != DispatchStatus.DISPATCH_PENDING) {
            throw new DispatchStatusTransitionException(status, DispatchStatus.DISPATCH_COMPLETED);
        }
        this.status = DispatchStatus.DISPATCH_COMPLETED;
    }

    public void updateMessageContent(final String message) {
        this.messageContent = message;
    }

    public Long getId() {
        return id;
    }

    public String getBuyerToken() {
        return buyerToken;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public String getBuyerPhoneNumber() {
        return buyerPhoneNumber;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getItemType() {
        return itemType;
    }

    public boolean isHighValueItem() {
        return highValueItem;
    }

    public String getProductLink() {
        return productLink;
    }

    public List<String> getItemImageUrls() {
        return List.copyOf(itemImageUrls);
    }

    public Long getEstimateRequestId() {
        return estimateRequestId;
    }

    public DispatchStatus getStatus() {
        return status;
    }

    public SellerInput getSellerInput() {
        return sellerInput;
    }

    public LocalDateTime getSellerInputCompletedAt() {
        return sellerInputCompletedAt;
    }

    public Integer getFinalQuotedAmount() {
        return finalQuotedAmount;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public LocalDateTime getAmountCheckedAt() {
        return amountCheckedAt;
    }

    public String getOperatorNote() {
        return operatorNote;
    }

    public String getClosedReason() {
        return closedReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
