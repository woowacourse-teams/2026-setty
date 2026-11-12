package setty.aisupport;

import setty.platform.OrderSupportReader;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Storage/wire boundary. Never drop absent fields or reorder old card JSON. */
final class SupportCardCodec {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private SupportCardCodec() {}

    static SupportCard read(Object value) {
        if (value instanceof SupportCard card) return card;
        JsonNode node = JSON.valueToTree(value);
        return switch (node.path("type").asString()) {
            case "ORDERS" ->
                    new OrdersCard(
                            node.path("orders")
                                    .valueStream()
                                    .map(
                                            v ->
                                                    JSON.convertValue(
                                                            v, SupportPresentation.OrderView.class))
                                    .toList(),
                            node.hasNonNull("nextCursor")
                                    ? JSON.convertValue(node.get("nextCursor"), Long.class)
                                    : null,
                            node.hasNonNull("hasUndatedOrders")
                                    ? JSON.convertValue(node.get("hasUndatedOrders"), Boolean.class)
                                    : null,
                            node.hasNonNull("excludedUndatedCount")
                                    ? JSON.convertValue(
                                            node.get("excludedUndatedCount"), Long.class)
                                    : null,
                            node.hasNonNull("ordering")
                                    ? JSON.convertValue(node.get("ordering"), String.class)
                                    : null,
                            node.hasNonNull("search")
                                    ? JSON.convertValue(node.get("search"), Map.class)
                                    : null,
                            node.hasNonNull("pageSize")
                                    ? JSON.convertValue(node.get("pageSize"), Integer.class)
                                    : null,
                            node.hasNonNull("confirmLatest")
                                    ? JSON.convertValue(node.get("confirmLatest"), Boolean.class)
                                    : null,
                            node.hasNonNull("purpose")
                                    ? JSON.convertValue(node.get("purpose"), String.class)
                                    : null,
                            node);
            case "ELIGIBILITY" ->
                    new EligibilityCard(
                            node.hasNonNull("order")
                                    ? JSON.convertValue(
                                            node.get("order"), SupportPresentation.OrderView.class)
                                    : null,
                            node.hasNonNull("deliveryStatus")
                                    ? JSON.convertValue(node.get("deliveryStatus"), String.class)
                                    : null,
                            node.hasNonNull("eligibility")
                                    ? JSON.convertValue(node.get("eligibility"), String.class)
                                    : null,
                            node.hasNonNull("reasonCode")
                                    ? JSON.convertValue(node.get("reasonCode"), String.class)
                                    : null,
                            node.hasNonNull("observedAt")
                                    ? JSON.convertValue(node.get("observedAt"), Instant.class)
                                    : null,
                            node.hasNonNull("policyVersion")
                                    ? JSON.convertValue(node.get("policyVersion"), String.class)
                                    : null,
                            node.hasNonNull("cancellationOffer")
                                    ? JSON.convertValue(
                                            node.get("cancellationOffer"), Boolean.class)
                                    : null,
                            node.hasNonNull("confirmationExpiresAt")
                                    ? JSON.convertValue(
                                            node.get("confirmationExpiresAt"), Instant.class)
                                    : null,
                            node.hasNonNull("confirmationOnly")
                                    ? JSON.convertValue(node.get("confirmationOnly"), Boolean.class)
                                    : null,
                            node);
            case "CANCELLATION_REQUEST" ->
                    new CancellationRequestCard(
                            node.hasNonNull("order")
                                    ? JSON.convertValue(
                                            node.get("order"), SupportPresentation.OrderView.class)
                                    : null,
                            node.hasNonNull("requestId")
                                    ? JSON.convertValue(node.get("requestId"), String.class)
                                    : null,
                            node.hasNonNull("acceptedAt")
                                    ? JSON.convertValue(node.get("acceptedAt"), Instant.class)
                                    : null,
                            node.hasNonNull("status")
                                    ? JSON.convertValue(node.get("status"), String.class)
                                    : null,
                            node.hasNonNull("historical")
                                    ? JSON.convertValue(node.get("historical"), Boolean.class)
                                    : null,
                            node.hasNonNull("previousStatus")
                                    ? JSON.convertValue(node.get("previousStatus"), String.class)
                                    : null,
                            node);
            default -> throw new IllegalArgumentException("Unknown stored card type");
        };
    }

    static SupportCard with(SupportCard card, String key, Object value) {
        ObjectNode wire = (ObjectNode) card.wire().deepCopy();
        wire.set(key, JSON.valueToTree(value));
        return read(wire);
    }

    static Map<String, Object> fields(SupportCard card) {
        return JSON.convertValue(card.wire(), LinkedHashMap.class);
    }

    static SupportCard orders(
            OrderSupportReader.SearchResult found, Map<String, Object> search, int pageSize) {
        var card = new LinkedHashMap<String, Object>();
        card.put("type", "ORDERS");
        card.put("orders", found.orders().stream().map(SupportPresentation::view).toList());
        card.put("nextCursor", found.nextCursor());
        card.put("hasUndatedOrders", found.hasUndatedOrders());
        card.put("excludedUndatedCount", found.excludedUndatedCount());
        card.put("ordering", "ORDER_ID_DESC");
        card.put("search", search);
        card.put("pageSize", pageSize);
        return read(card);
    }

    static SupportCard latest(OrderSupportReader.SearchResult found, Map<String, Object> args) {
        var card = new LinkedHashMap<String, Object>();
        card.put("type", "ORDERS");
        card.put("orders", found.orders().stream().map(SupportPresentation::view).toList());
        card.put("nextCursor", null);
        card.put("hasUndatedOrders", found.hasUndatedOrders());
        card.put("excludedUndatedCount", found.excludedUndatedCount());
        card.put("ordering", "ORDER_ID_DESC");
        card.put("search", new LinkedHashMap<>(args));
        card.put("confirmLatest", true);
        card.put("purpose", "CANCEL_CHECK");
        card.put("pageSize", 1);
        return read(card);
    }

    static SupportCard target(OrderSupportReader.SearchResult found, Map<String, Object> search) {
        var card = new LinkedHashMap<String, Object>();
        card.put("type", "ORDERS");
        card.put("orders", found.orders().stream().map(SupportPresentation::view).toList());
        card.put("confirmLatest", true);
        card.put("purpose", "CANCEL_CHECK");
        card.put("pageSize", 1);
        card.put("hasUndatedOrders", found.hasUndatedOrders());
        card.put("excludedUndatedCount", found.excludedUndatedCount());
        card.put("ordering", "ORDER_ID_DESC");
        card.put("search", search);
        card.put("nextCursor", null);
        return read(card);
    }

    static SupportCard context(OrderSupportReader.Context context) {
        var card = new LinkedHashMap<String, Object>();
        card.put("type", "ELIGIBILITY");
        card.put("order", SupportPresentation.view(context.order()));
        card.put("deliveryStatus", context.deliveryStatus());
        card.put("eligibility", context.eligibility().name());
        card.put("reasonCode", context.reasonCode());
        card.put("observedAt", context.observedAt());
        card.put("policyVersion", context.policyVersion());
        if (context.eligibility() == OrderSupportReader.Eligibility.ELIGIBLE) {
            card.put("cancellationOffer", true);
            card.put("confirmationExpiresAt", context.observedAt().plus(Duration.ofMinutes(5)));
        }
        return read(card);
    }

    static SupportCard receipt(
            SupportPresentation.OrderView order,
            String requestId,
            Instant at,
            boolean historical,
            String previousStatus) {
        if (historical)
            return read(
                    OrderedJson.of(
                            "type",
                            "CANCELLATION_REQUEST",
                            "order",
                            order,
                            "requestId",
                            requestId,
                            "acceptedAt",
                            at,
                            "status",
                            "ACCEPTED",
                            "historical",
                            true,
                            "previousStatus",
                            previousStatus));
        return read(
                OrderedJson.of(
                        "type",
                        "CANCELLATION_REQUEST",
                        "order",
                        order,
                        "requestId",
                        requestId,
                        "acceptedAt",
                        at,
                        "status",
                        "ACCEPTED"));
    }
}
