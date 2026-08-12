package setty.dispatch.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public class SellerInput {
    private String sellerName;
    private String sellerPhoneNumber;
    private String pickupAddress;
    private String availablePickupTime;

    protected SellerInput() {
    }

    public SellerInput(
            final String sellerName,
            final String sellerPhoneNumber,
            final String pickupAddress,
            final String availablePickupTime
    ) {
        this.sellerName = sellerName;
        this.sellerPhoneNumber = sellerPhoneNumber;
        this.pickupAddress = pickupAddress;
        this.availablePickupTime = availablePickupTime;
    }

    public boolean isPresent() {
        return sellerName != null;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getSellerPhoneNumber() {
        return sellerPhoneNumber;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public String getAvailablePickupTime() {
        return availablePickupTime;
    }
}
