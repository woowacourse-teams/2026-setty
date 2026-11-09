-- 주문·배송 상태가 바뀔 때 구매자·판매자에게 가는 알림. 키워드 알림(listing_notifications)과 따로 둔다.
-- 주문은 다른 모듈 테이블이고, 회원 FK는 비동기로 쌓이는 행이 테스트 정리와 엉키므로 둘 다 걸지 않는다. 탈퇴 때 알림 모듈이 직접 지운다.
CREATE TABLE IF NOT EXISTS order_notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    listing_id  BIGINT       NOT NULL,
    member_id   BIGINT       NOT NULL,
    recipient   VARCHAR(10)  NOT NULL,              -- BUYER / SELLER. 누르면 갈 화면을 고른다
    kind        VARCHAR(40)  NOT NULL,              -- ORDER_CONFIRMED, LISTING_SOLD 등. 문구를 정한다
    status      VARCHAR(10)  NOT NULL,              -- PENDING / SENT / FAILED
    attempts    INT          NOT NULL DEFAULT 0,    -- 발송 시도 횟수. 3회 실패 시 FAILED
    occurred_at TIMESTAMP(6) NOT NULL,              -- 상태가 바뀐 시각. 재배차처럼 같은 종류가 다시 오면 이 값이 다르다
    created_at  TIMESTAMP(6) NOT NULL,
    sent_at     TIMESTAMP(6) NULL,
    acked_at    TIMESTAMP(6) NULL,                  -- 기기 수신 확인(발송 측정값)
    read_at     TIMESTAMP(6) NULL,                  -- 알림함에서 읽은 시각
    PRIMARY KEY (id),
    CONSTRAINT uk_order_notifications_event UNIQUE (order_id, member_id, kind, occurred_at),
    INDEX idx_order_notifications_member (member_id, created_at, id)
);
