package setty.delivery.application.readmodel;

import java.util.List;

public record DeliveryPage<T>(List<T> items, String nextCursor) {
}
