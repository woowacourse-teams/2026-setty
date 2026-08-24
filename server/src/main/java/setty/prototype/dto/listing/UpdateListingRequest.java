package setty.prototype.dto.listing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import setty.prototype.web.StrictPriceDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 변경할 필드만 담는다. 담기지 않은 필드는 그대로 둔다.
 */
public record UpdateListingRequest(
        @Size(min = 1, max = 100) String title,
        @Size(min = 1, max = 500) String description,
        @PositiveOrZero @Max(99_999_999)
        @JsonDeserialize(using = StrictPriceDeserializer.class)
        Integer price,
        @Size(min = 1, max = 50) String pickupTimeText,
        Boolean canHelpMove
) {
    public boolean hasNoChange() {
        return title == null && description == null && price == null && pickupTimeText == null && canHelpMove == null;
    }
}
