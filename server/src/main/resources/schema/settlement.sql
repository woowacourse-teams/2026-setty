-- 매 부팅 시 실행되므로 모든 문장은 멱등이어야 한다. 실행 순서는 application.yml의 spring.sql.init.schema-locations를 따른다.

-- 정산 (settlement 계층). 주문별 판매자(물품대금)·기사(배송비) 정산을 받는 사람마다 1행으로 기록한다.
-- 다른 모듈의 ID는 이벤트로 받은 값만 저장하고 FK를 두지 않는다. 실제 지급은 하지 않으며 출금 가능 금액은 CONFIRMED 합계다.
CREATE TABLE IF NOT EXISTS settlements (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    payee_type   VARCHAR(20)  NOT NULL,   -- SELLER(members.id) / DRIVER(delivery_member.id)
    payee_id     BIGINT       NOT NULL,
    listing_id   BIGINT       NULL,       -- 판매자 정산만
    amount       INT          NOT NULL,
    status       VARCHAR(20)  NOT NULL,   -- PENDING / CONFIRMED / CANCELLED
    created_at   TIMESTAMP(6) NOT NULL,
    confirmed_at TIMESTAMP(6) NULL,
    cancelled_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlements_order_payee (order_id, payee_type),
    INDEX idx_settlements_payee (payee_type, payee_id, status),
    CONSTRAINT chk_settlements_amount CHECK (amount >= 0)
);

-- 주문별 정산 결론. 결론 이벤트가 정산 행보다 먼저 와도 잃지 않도록 따로 기록한다.
-- 같은 주문의 정산 처리를 이 행의 잠금으로 직렬화하므로 결론 전에도 행을 만든다.
CREATE TABLE IF NOT EXISTS settlement_order (
    order_id   BIGINT       NOT NULL,
    outcome    VARCHAR(20)  NULL,         -- CONFIRMED(판매 완료) / CANCELLED. 결론 전 NULL
    decided_at TIMESTAMP(6) NULL,
    PRIMARY KEY (order_id)
);
