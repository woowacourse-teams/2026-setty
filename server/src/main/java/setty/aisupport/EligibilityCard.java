package setty.aisupport;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

record EligibilityCard(
        SupportPresentation.OrderView order,
        String deliveryStatus,
        String eligibility,
        String reasonCode,
        Instant observedAt,
        String policyVersion,
        Boolean cancellationOffer,
        Instant confirmationExpiresAt,
        Boolean confirmationOnly,
        JsonNode wire)
        implements SupportCard {
    public SupportDtos.CardKind type() {
        return SupportDtos.CardKind.ELIGIBILITY;
    }
}
