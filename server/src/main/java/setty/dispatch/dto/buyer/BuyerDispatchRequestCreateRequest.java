package setty.dispatch.dto.buyer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BuyerDispatchRequestCreateRequest(
        @NotBlank @Size(max = 50) String buyerName,
        @NotBlank @Pattern(regexp = "^01\\d-?\\d{3,4}-?\\d{4}$") String buyerPhoneNumber,
        @NotBlank @Size(max = 255) String deliveryAddress,
        @NotBlank @Size(max = 100) String itemType,
        @NotNull Boolean highValueItem,
        @NotBlank @Size(max = 500) String productLink,
        @Size(max = 5) List<@NotBlank @Size(max = 500) String> itemImageUrls,
        Long estimateRequestId
) {
    public List<String> itemImageUrlsOrEmpty() {
        return itemImageUrls == null ? List.of() : itemImageUrls;
    }
}
