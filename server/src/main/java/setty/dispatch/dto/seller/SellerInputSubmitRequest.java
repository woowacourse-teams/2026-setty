package setty.dispatch.dto.seller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import setty.common.phone.PhoneNumbers;
import setty.dispatch.domain.SellerInput;

public record SellerInputSubmitRequest(
        @NotBlank @Size(max = 50) String sellerName,
        @NotBlank @Pattern(regexp = "^01\\d-?\\d{3,4}-?\\d{4}$") String sellerPhoneNumber,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 100) String availablePickupTime,
        Boolean privacyConsent,
        @Size(max = 20) String privacyPolicyVersion
) {
    public boolean consented() {
        return Boolean.TRUE.equals(privacyConsent);
    }

    public SellerInput toSellerInput() {
        return new SellerInput(
                sellerName,
                PhoneNumbers.normalize(sellerPhoneNumber),
                pickupAddress,
                availablePickupTime
        );
    }
}
