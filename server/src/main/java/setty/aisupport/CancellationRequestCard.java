package setty.aisupport;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

record CancellationRequestCard(
        SupportPresentation.OrderView order,
        String requestId,
        Instant acceptedAt,
        String status,
        Boolean historical,
        String previousStatus,
        JsonNode wire)
        implements SupportCard {
    public SupportDtos.CardKind type() {
        return SupportDtos.CardKind.CANCELLATION_REQUEST;
    }
}
