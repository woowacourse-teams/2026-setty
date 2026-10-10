package setty.notification.keyword.presentation;

import jakarta.validation.constraints.NotBlank;

public record KeywordRequest(@NotBlank String keyword) {
}
