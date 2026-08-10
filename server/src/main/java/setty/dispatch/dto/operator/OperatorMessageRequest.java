package setty.dispatch.dto.operator;

import jakarta.validation.constraints.NotBlank;

public record OperatorMessageRequest(
        @NotBlank String messageContent
) {
}
