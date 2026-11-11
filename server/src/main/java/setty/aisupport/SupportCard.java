package setty.aisupport;

import com.fasterxml.jackson.annotation.JsonValue;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Typed card values. The codec preserves historical JSON omissions and field order. */
sealed interface SupportCard permits OrdersCard, EligibilityCard, CancellationRequestCard {
    SupportDtos.CardKind type();

    @JsonValue
    JsonNode wire();

    default List<SupportPresentation.OrderView> orders() {
        return null;
    }

    default Long nextCursor() {
        return null;
    }

    default Boolean hasUndatedOrders() {
        return null;
    }

    default Long excludedUndatedCount() {
        return null;
    }

    default String ordering() {
        return null;
    }

    default Map<String, Object> search() {
        return null;
    }

    default Integer pageSize() {
        return null;
    }

    default Boolean confirmLatest() {
        return null;
    }

    default String purpose() {
        return null;
    }

    default SupportPresentation.OrderView order() {
        return null;
    }

    default String deliveryStatus() {
        return null;
    }

    default String eligibility() {
        return null;
    }

    default String reasonCode() {
        return null;
    }

    default Instant observedAt() {
        return null;
    }

    default String policyVersion() {
        return null;
    }

    default Boolean cancellationOffer() {
        return null;
    }

    default Instant confirmationExpiresAt() {
        return null;
    }

    default Boolean confirmationOnly() {
        return null;
    }

    default String requestId() {
        return null;
    }

    default Instant acceptedAt() {
        return null;
    }

    default String status() {
        return null;
    }

    default Boolean historical() {
        return null;
    }

    default String previousStatus() {
        return null;
    }
}
