package setty.prototype.dto.message;

import java.util.List;

public record MessageListResponse(
        Long listingId,
        List<MessageResponse> items
) {
}
