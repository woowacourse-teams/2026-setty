package setty.platform.favorite.domain;

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
@Table(name = "favorites")
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Favorite() {
    }

    public Favorite(final Long memberId, final Long listingId) {
        if (memberId == null || listingId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.memberId = memberId;
        this.listingId = listingId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getListingId() {
        return listingId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
