package setty.delivery.api.dto;

import java.util.List;

/** cell은 서버가 마지막으로 받은 배송원 칸이다. 위치를 아직 받지 못했으면 null이고 items는 비어 있다. */
public record BundleCandidatesResponse(Integer cell, List<DeliveryRequestSummaryResponse> items) {
}
