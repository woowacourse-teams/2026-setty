package setty.delivery.api.dto;

import java.util.List;

public record DeliveryPageResponse<T>(List<T> items, String nextCursor) {
}
