package setty.notification.keyword.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
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

    @Column(length = 20)
    private String category;

    @Column(name = "min_price")
    private Integer minPrice;

    @Column(name = "max_price")
    private Integer maxPrice;

    @Column(name = "exclude_keyword", length = MAXIMUM_KEYWORD_LENGTH)
    private String excludeKeyword;

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
