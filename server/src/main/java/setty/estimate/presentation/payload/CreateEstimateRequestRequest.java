package setty.estimate.presentation.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;
import setty.estimate.presentation.PhoneNumberDeserializer;

public record CreateEstimateRequestRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 10, message = "이름은 10자 이하여야 합니다.")
        @Pattern(regexp = "^\\S+$", message = "이름에 공백을 포함할 수 없습니다.")
        String name,

        @JsonDeserialize(using = PhoneNumberDeserializer.class)
        @NotBlank(message = "연락처는 필수입니다.")
        @Pattern(regexp = "^010\\d{8}$", message = "연락처는 010으로 시작하는 숫자 11자리여야 합니다.")
        String phoneNumber,

        @NotBlank(message = "거래 지역은 필수입니다.")
        @Size(max = 100, message = "거래 지역은 100자 이하여야 합니다.")
        String tradeArea,

        @NotBlank(message = "물품 종류는 필수입니다.")
        @Size(max = 100, message = "물품 종류는 100자 이하여야 합니다.")
        String itemType,

        @NotNull(message = "50만 원 초과 여부는 필수입니다.")
        Boolean highValueItem,

        @Size(max = 500, message = "당근 링크는 500자 이하여야 합니다.")
        String productLink
) {
}
