package setty.prototype.repository;

import java.time.LocalDateTime;

/**
 * 판매자 마이페이지에서 매물별 쪽지 수와 최근 쪽지 시각을 한 번에 읽기 위한 조회 결과다.
 */
public interface ListingMessageStat {
    Long getListingId();

    long getMessageCount();

    LocalDateTime getLatestMessageAt();
}
