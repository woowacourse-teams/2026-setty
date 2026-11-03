package setty.delivery.application.readmodel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 배송 대기 목록 한 번의 조회 결과. 캐시에 보관해 여러 요청이 함께 쓰므로 바꿀 수 없게 만든다.
 * 사진이 없는 주문은 지금처럼 썸네일 키를 두지 않는다.
 */
public record AvailableRequests(List<DeliveryRequest.Summary> items, Map<Long, String> thumbnailUrls, String nextCursor) {

    public AvailableRequests {
        items = List.copyOf(items);
        thumbnailUrls = thumbnailUrls.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String thumbnailUrl(final long orderId) {
        return thumbnailUrls.get(orderId);
    }
}
