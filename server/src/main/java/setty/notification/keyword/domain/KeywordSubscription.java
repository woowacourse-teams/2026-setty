package setty.notification.keyword.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Entity
@Table(name = "keyword_subscriptions")
public class KeywordSubscription {

    public static final int MAXIMUM_KEYWORD_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = MAXIMUM_KEYWORD_LENGTH)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private KeywordCategory category;

    @Column(name = "min_price")
    private Integer minPrice;

    @Column(name = "max_price")
    private Integer maxPrice;

    @Column(name = "max_delivery_fee")
    private Integer maxDeliveryFee;

    @Column(name = "max_total_price")
    private Integer maxTotalPrice;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "keyword_exclusions", joinColumns = @JoinColumn(name = "subscription_id"))
    @Column(name = "word", nullable = false, length = MAXIMUM_KEYWORD_LENGTH)
    private List<String> excludeKeywords = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KeywordSubscription() {
    }

    public KeywordSubscription(final Long memberId, final String keyword) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.memberId = memberId;
        this.keyword = normalize(keyword);
        this.createdAt = Instant.now();
    }

    public static String normalize(final String keyword) {
        if (keyword == null) {
            throw new BusinessException(ErrorCode.INVALID_KEYWORD);
        }
        final String normalized = keyword.strip();
        if (normalized.isEmpty() || normalized.length() > MAXIMUM_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_KEYWORD);
        }
        return normalized;
    }

    public void changeConditions(final KeywordConditions conditions) {
        if (conditions.excludeKeywords().stream().anyMatch(keyword::equalsIgnoreCase)) {
            throw new BusinessException(ErrorCode.INVALID_KEYWORD_CONDITION);
        }
        this.category = conditions.category();
        this.minPrice = conditions.minPrice();
        this.maxPrice = conditions.maxPrice();
        this.maxDeliveryFee = conditions.maxDeliveryFee();
        this.maxTotalPrice = conditions.maxTotalPrice();
        this.excludeKeywords.clear();
        this.excludeKeywords.addAll(conditions.excludeKeywords());
    }

    public KeywordConditions getConditions() {
        return new KeywordConditions(category, minPrice, maxPrice, maxDeliveryFee, maxTotalPrice, excludeKeywords);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getKeyword() {
        return keyword;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
