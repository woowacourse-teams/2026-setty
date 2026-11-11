package setty.aisupport;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

record OrdersCard(
        List<SupportPresentation.OrderView> orders,
        Long nextCursor,
        Boolean hasUndatedOrders,
        Long excludedUndatedCount,
        String ordering,
        Map<String, Object> search,
        Integer pageSize,
        Boolean confirmLatest,
        String purpose,
        JsonNode wire)
        implements SupportCard {
    public SupportDtos.CardKind type() {
        return SupportDtos.CardKind.ORDERS;
    }
}
