package setty.prototype.dto.listing;

import jakarta.validation.constraints.Size;

/**
 * 변경할 필드만 담는다. 담기지 않은 필드는 그대로 둔다.
 */
public record UpdateListingRequest(
        @Size(min = 1, max = 100) String title,
        @Size(min = 1, max = 500) String description,
        @Size(min = 1, max = 50) String pickupTimeText,
        Boolean canHelpMove
) {
    public boolean hasNoChange() {
        return title == null && description == null && pickupTimeText == null && canHelpMove == null;
    }
}
