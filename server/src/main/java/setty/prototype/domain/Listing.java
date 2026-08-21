package setty.prototype.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import setty.common.time.SeoulDateTime;

@Entity
@Table(name = "prototype_listing")
public class Listing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member seller;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 50)
    private String pickupTimeText;

    @Column(nullable = false)
    private boolean canHelpMove;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private List<ListingImage> images = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Listing() {
    }

    public Listing(
            final Member seller,
            final String title,
            final String description,
            final String pickupTimeText,
            final boolean canHelpMove,
            final List<String> imageUrls
    ) {
        final LocalDateTime now = SeoulDateTime.now();
        this.seller = seller;
        this.title = title;
        this.description = description;
        this.pickupTimeText = pickupTimeText;
        this.canHelpMove = canHelpMove;
        for (int index = 0; index < imageUrls.size(); index++) {
            this.images.add(new ListingImage(this, imageUrls.get(index), index + 1));
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 전달된 필드만 바꾼다. 계약상 수정 요청은 변경할 필드만 담는다.
     */
    public void update(
            final String title,
            final String description,
            final String pickupTimeText,
            final Boolean canHelpMove
    ) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (pickupTimeText != null) {
            this.pickupTimeText = pickupTimeText;
        }
        if (canHelpMove != null) {
            this.canHelpMove = canHelpMove;
        }
        this.updatedAt = SeoulDateTime.now();
    }

    public boolean isOwnedBy(final Long memberId) {
        return seller.getId().equals(memberId);
    }

    public String thumbnailUrl() {
        if (images.isEmpty()) {
            return null;
        }

        return images.getFirst().getUrl();
    }

    public Long getId() {
        return id;
    }

    public Member getSeller() {
        return seller;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPickupTimeText() {
        return pickupTimeText;
    }

    public boolean isCanHelpMove() {
        return canHelpMove;
    }

    public List<ListingImage> getImages() {
        return List.copyOf(images);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
