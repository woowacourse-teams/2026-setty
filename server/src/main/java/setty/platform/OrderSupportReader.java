package setty.platform;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 본인 주문에 한정된 읽기 전용 상담 경계. 날짜는 KST 생성일이며 양끝을 포함한다. */
public interface OrderSupportReader {
    SearchResult findMyOrders(Long buyerId, Search query);

    Context getContext(Long buyerId, Long orderId);

    boolean isActiveMember(Long buyerId);

    /** 호출자가 연 짧은 트랜잭션 안에서만 사용한다. 회원→상담 순서로 잠근다. */
    boolean lockActiveMember(Long buyerId);

    record Search(
            Long orderId,
            String title,
            LocalDate fromDate,
            LocalDate toDate,
            Long beforeId,
            Integer pageSize,
            boolean latestOnly,
            boolean cancellationTargets,
            Long excludedOrderId,
            boolean cancelledOnly) {
        public static Search browse(
                Long orderId,
                String title,
                LocalDate fromDate,
                LocalDate toDate,
                Long beforeId,
                Integer pageSize) {
            return new Search(
                    orderId, title, fromDate, toDate, beforeId, pageSize, false, false, null,
                    false);
        }

        public static Search latest(String title) {
            return new Search(null, title, null, null, null, 1, true, true, null, false);
        }

        public static Search cancellationTargets(
                Long orderId,
                String title,
                LocalDate fromDate,
                LocalDate toDate,
                Long beforeId,
                Integer pageSize,
                Long excludedOrderId) {
            return browse(orderId, title, fromDate, toDate, beforeId, pageSize)
                    .forCancellation()
                    .excluding(excludedOrderId);
        }

        public Search forCancellation() {
            return new Search(
                    orderId,
                    title,
                    fromDate,
                    toDate,
                    beforeId,
                    pageSize,
                    latestOnly,
                    true,
                    excludedOrderId,
                    cancelledOnly);
        }

        public Search onlyCancelled() {
            return new Search(
                    orderId,
                    title,
                    fromDate,
                    toDate,
                    beforeId,
                    pageSize,
                    latestOnly,
                    cancellationTargets,
                    excludedOrderId,
                    true);
        }

        public Search excluding(Long excludedOrderId) {
            return new Search(
                    orderId,
                    title,
                    fromDate,
                    toDate,
                    beforeId,
                    pageSize,
                    latestOnly,
                    cancellationTargets,
                    excludedOrderId,
                    cancelledOnly);
        }
    }

    record SearchResult(
            List<Candidate> orders,
            Long nextCursor,
            boolean hasUndatedOrders,
            long excludedUndatedCount) {}

    /** title과 createdAt은 과거 데이터에서 null일 수 있다. */
    record Candidate(
            Long orderId,
            String title,
            Instant createdAt,
            String orderStatus,
            String thumbnailUrl,
            Integer totalPrice,
            String deliveryStatus) {
        public Candidate(Long orderId, String title, Instant createdAt, String orderStatus) {
            this(orderId, title, createdAt, orderStatus, null, null, null);
        }
    }

    record Context(
            Candidate order,
            String deliveryStatus,
            Eligibility eligibility,
            String reasonCode,
            Instant observedAt,
            String policyVersion) {}

    enum Eligibility {
        ELIGIBLE,
        INELIGIBLE,
        UNKNOWN
    }
}
