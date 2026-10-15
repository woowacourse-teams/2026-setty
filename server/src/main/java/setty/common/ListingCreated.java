package setty.common;

import java.time.Instant;

/** 매물이 최초 등록된 사실. 키워드 구독자 알림 생성·발송에 사용한다. 수정·재등록에는 발행하지 않는다. */
public record ListingCreated(
        Long listingId,
        Long sellerId,
        String title,
        Instant createdAt
) {
}
