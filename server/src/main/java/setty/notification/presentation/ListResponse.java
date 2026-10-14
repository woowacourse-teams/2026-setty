package setty.notification.presentation;

import java.util.List;

public record ListResponse<T>(List<T> items) {
}
