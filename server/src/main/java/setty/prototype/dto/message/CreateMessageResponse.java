package setty.prototype.dto.message;

import java.time.OffsetDateTime;
import setty.common.time.SeoulDateTime;
import setty.prototype.domain.ListingMessage;

public record CreateMessageResponse(
        Long messageId,
        OffsetDateTime createdAt
) {
    public static CreateMessageResponse from(final ListingMessage message) {
        return new CreateMessageResponse(
                message.getId(),
                SeoulDateTime.toOffsetDateTime(message.getCreatedAt())
        );
    }
}
