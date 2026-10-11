-- 매 부팅 시 실행되므로 모든 문장은 멱등이어야 한다. 실행 순서는 application.yml의 spring.sql.init.schema-locations를 따른다.

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
