package setty.estimate.application.command;

public record CreateEstimateRequestCommand(
        String name,
        String phoneNumber,
        String tradeArea,
        String itemType,
        boolean highValueItem,
        String productLink
) {
}
