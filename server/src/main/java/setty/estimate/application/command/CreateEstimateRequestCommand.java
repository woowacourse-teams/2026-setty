package setty.estimate.application.command;

public record CreateEstimateRequestCommand(
        String name,
        String phoneNumber,
        String tradeArea,
        String itemType,
        boolean highValueItem,
        String productLink,
        Boolean privacyConsent,
        String privacyPolicyVersion
) {
    public boolean consented() {
        return Boolean.TRUE.equals(privacyConsent);
    }
}
