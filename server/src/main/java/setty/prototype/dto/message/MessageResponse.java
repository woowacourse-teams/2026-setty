package setty.prototype.dto.message;

import java.time.OffsetDateTime;
import setty.common.time.SeoulDateTime;
import setty.prototype.domain.ListingMessage;

/**
 * 구매자를 식별할 수 있는 값은 담지 않는다.
 */
public record MessageResponse(
        Long id,
        String content,
        OffsetDateTime createdAt
) {
    public static MessageResponse from(final ListingMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getContent(),
                SeoulDateTime.toOffsetDateTime(message.getCreatedAt())
        );
    }
}
