package setty.aisupport;

import setty.platform.OrderSupportReader;

import java.time.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

final class SupportPresentation {
    record OrderView(
            Long orderId,
            String title,
            Instant createdAt,
            String createdDateKst,
            String orderStatus,
            String thumbnailUrl,
            Integer totalPrice,
            String deliveryStatus) {}

    static OrderView view(OrderSupportReader.Candidate c) {
        return new OrderView(
                c.orderId(),
                c.title(),
                c.createdAt(),
                c.createdAt() == null
                        ? null
                        : c.createdAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString(),
                c.orderStatus(),
                c.thumbnailUrl(),
                c.totalPrice(),
                c.deliveryStatus());
    }

    static String searchDescription(Map<String, Object> search, String purpose) {
        var conditions = new ArrayList<String>();
        if (search.get("title") != null) conditions.add("'" + search.get("title") + "'");
        if (search.get("fromDate") != null) conditions.add("주문일 " + search.get("fromDate") + "부터");
        if (search.get("toDate") != null) conditions.add("주문일 " + search.get("toDate") + "까지");
        if (search.get("orderId") != null) conditions.add("주문 #" + search.get("orderId"));
        if ("CANCEL_CHECK".equals(purpose)) conditions.add("취소 완료 제외");
        if ("CANCELLED_CHECK".equals(purpose)) conditions.add("취소 완료만");
        return conditions.isEmpty() ? "전체 주문" : String.join(", ", conditions);
    }

    static String renderLatest(OrderSupportReader.SearchResult found) {
        if (found.orders().isEmpty()) return "현재 로그인한 계정에서 취소 완료를 제외한 조건에 맞는 주문이 없어요.";
        return "가장 최근에 주문하신 상품이에요. 취소하려는 주문이 맞나요? 맞으면 취소 가능 여부를 확인할게요.";
    }

    static String renderOrders(OrderSupportReader.SearchResult found) {
        return renderOrders(found, false);
    }

    static String renderOrders(OrderSupportReader.SearchResult found, boolean cancelledOnly) {
        if (cancelledOnly) {
            if (found.orders().isEmpty()) return "현재 로그인한 계정에서 해당 조건의 취소된 주문을 찾지 못했어요.";
            return found.orders().stream()
                            .map(o -> o.orderId() + "번")
                            .collect(Collectors.joining(", "))
                    + " 주문이 취소된 상태예요. 아래 카드에서 확인해 주세요.";
        }
        if (found.orders().isEmpty())
            return "현재 로그인한 계정에서 조건에 맞는 주문을 찾지 못했어요. 상품명이나 주문 날짜를 바꿔 찾아보세요.";
        return "주문 " + found.orders().size() + "건을 표시했어요. 아래 카드에서 주문을 확인해 주세요.";
    }
}
