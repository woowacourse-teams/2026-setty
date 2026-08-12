package setty.estimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "estimate_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstimateRequest {
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String name;

    @Column(nullable = false, length = 11)
    private String phoneNumber;

    @Column(nullable = false, length = 100)
    private String tradeArea;

    @Column(nullable = false, length = 100)
    private String itemType;

    @Column(nullable = false)
    private boolean highValueItem;

    @Column(length = 500)
    private String productLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstimateRequestStatus status;

    private LocalDateTime privacyConsentedAt;

    @Column(length = 20)
    private String privacyPolicyVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private EstimateRequest(
            final String name,
            final String phoneNumber,
            final String tradeArea,
            final String itemType,
            final boolean highValueItem,
            final String productLink
    ) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.tradeArea = tradeArea;
        this.itemType = itemType;
        this.highValueItem = highValueItem;
        this.productLink = productLink;
        this.status = EstimateRequestStatus.PENDING_REVIEW;
        this.createdAt = LocalDateTime.now(SEOUL_ZONE_ID);
    }

    public static EstimateRequest pendingReview(
            final String name,
            final String phoneNumber,
            final String tradeArea,
            final String itemType,
            final boolean highValueItem,
            final String productLink
    ) {
        return new EstimateRequest(name, phoneNumber, tradeArea, itemType, highValueItem, productLink);
    }

    public String getProductLink() {
        return productLink;
    }

    public void recordPrivacyConsent(final String policyVersion) {
        this.privacyConsentedAt = LocalDateTime.now(SEOUL_ZONE_ID);
        this.privacyPolicyVersion = policyVersion;
    }

    public LocalDateTime getPrivacyConsentedAt() {
        return privacyConsentedAt;
    }

    public String getPrivacyPolicyVersion() {
        return privacyPolicyVersion;
    }

    public void markEstimateNotified() {
        if (status != EstimateRequestStatus.PENDING_REVIEW) {
            throw new InvalidEstimateRequestStatusException();
        }

        this.status = EstimateRequestStatus.ESTIMATE_NOTIFIED;
    }

}
