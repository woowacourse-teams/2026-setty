-- Flyway 최초 편입용: 빈 DB와 기존 DB 모두 지원한다. 적용 후에는 수정하지 말고 새 버전을 추가한다.

-- 키워드 구독 (notification 모듈). members FK가 있어 platform.sql 뒤에 실행한다.
-- category·min_price·max_price·exclude_keyword는 다음 스프린트 조건용. 이번에는 항상 NULL.
CREATE TABLE IF NOT EXISTS keyword_subscriptions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    member_id       BIGINT       NOT NULL,
    keyword         VARCHAR(20)  NOT NULL,          -- 양쪽 공백 제거 후 1~20자. 매칭은 콜레이션으로 대소문자 무시
    category        VARCHAR(20)  NULL,
    min_price       INT          NULL,
    max_price       INT          NULL,
    exclude_keyword VARCHAR(20)  NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_keyword_subscriptions_member_keyword UNIQUE (member_id, keyword),
    CONSTRAINT fk_keyword_subscriptions_member FOREIGN KEY (member_id) REFERENCES members (id),
    INDEX idx_keyword_subscriptions_keyword (keyword)
);

-- 매물 등록 알림. 매물·사용자 조합당 1건.
CREATE TABLE IF NOT EXISTS listing_notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id BIGINT       NOT NULL,
    member_id  BIGINT       NOT NULL,
    status     VARCHAR(10)  NOT NULL,              -- PENDING / SENT / FAILED
    attempts   INT          NOT NULL DEFAULT 0,    -- 발송 시도 횟수. 3회 실패 시 FAILED
    created_at TIMESTAMP(6) NOT NULL,
    sent_at    TIMESTAMP(6) NULL,                  -- 서버(DB) 시계 기준 발송 시각
    PRIMARY KEY (id),
    CONSTRAINT uk_listing_notifications_listing_member UNIQUE (listing_id, member_id),
    CONSTRAINT fk_listing_notifications_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_listing_notifications_member  FOREIGN KEY (member_id)  REFERENCES members (id),
    INDEX idx_listing_notifications_pending (listing_id, status, id),
    INDEX idx_listing_notifications_member (member_id, created_at, id)
);

-- 앱이 수신 즉시 보내는 ack를 받은 시각(서버 시계). 기기 도착 측정용. 기존 테이블이라 멱등 ALTER로 추가한다.
SET @listing_notifications_acked_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'listing_notifications'
      AND column_name = 'acked_at'
);
SET @add_listing_notifications_acked_at = IF(
    @listing_notifications_acked_at_exists = 0,
    'ALTER TABLE listing_notifications ADD COLUMN acked_at TIMESTAMP(6) NULL AFTER sent_at',
    'SELECT 1'
);
PREPARE add_listing_notifications_acked_at_statement FROM @add_listing_notifications_acked_at;
EXECUTE add_listing_notifications_acked_at_statement;
DEALLOCATE PREPARE add_listing_notifications_acked_at_statement;

-- 앱이 받은 FCM 등록 토큰. 토큰 하나는 기기 하나이며, 사용자 1명이 기기 여러 대를 가질 수 있다.
-- 같은 토큰이 다른 사용자로 다시 오면 기기 주인이 바뀐 것으로 보고 member_id를 갱신한다.
CREATE TABLE IF NOT EXISTS device_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    token      VARCHAR(255) NOT NULL,
    platform   VARCHAR(10)  NOT NULL,              -- ANDROID / IOS
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_device_tokens_token UNIQUE (token),
    CONSTRAINT fk_device_tokens_member FOREIGN KEY (member_id) REFERENCES members (id),
    INDEX idx_device_tokens_member (member_id)
);
