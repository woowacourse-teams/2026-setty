package setty.dispatch.dto.operator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record OperatorFinalAmountRequest(
        @NotNull @PositiveOrZero Integer finalQuotedAmount,
        @NotBlank String messageContent
) {
}
