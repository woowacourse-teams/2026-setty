-- 매 부팅 시 실행되므로 모든 문장은 멱등이어야 한다. 실행 순서는 application.yml의 spring.sql.init.schema-locations를 따른다.

-- 결제 (payment 계층). 토스페이먼츠 결제 결과(성공 DONE / 실패 ABORTED)를 1주문 1행으로 저장한다.
-- 주문은 결제 이전에 PENDING으로 먼저 생성되므로 payments.order_id는 항상 존재하는 주문을 가리킨다.
-- 실패 저장을 위해 payment_key·approved_at은 NULL 허용. 실패 후 재승인 시 같은 행을 DONE으로 전이한다.
-- 주문은 만료 후에도 보존한다. 기존 DB의 fk_payments_order는 아래에서 제거한다.
CREATE TABLE IF NOT EXISTS payments (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL,                  -- 결제 이전에 PENDING으로 존재하는 주문 (1주문 1행)
    payment_key   VARCHAR(200) NULL,                      -- 토스가 발급한 결제 키 (실패/ABORTED 시 NULL)
    toss_order_id VARCHAR(64)  NOT NULL,                  -- 토스 결제창 orderId (= 내부 주문 id)
    amount        INT          NOT NULL,                  -- 결제 금액 (매물 totalPrice와 일치)
    status        VARCHAR(20)  NOT NULL,                  -- 결제 상태 (DONE / ABORTED)
    approved_at   DATETIME     NULL,                      -- 토스 승인 시각 (실패/ABORTED 시 NULL)
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_order_id (order_id),
    UNIQUE KEY uk_payments_toss_order_id (toss_order_id)
);

SET @payments_order_fk_exists = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'payments'
      AND constraint_name = 'fk_payments_order' AND constraint_type = 'FOREIGN KEY'
);
SET @drop_payments_order_fk = IF(
    @payments_order_fk_exists > 0,
    'ALTER TABLE payments DROP FOREIGN KEY fk_payments_order',
    'SELECT 1'
);
PREPARE drop_payments_order_fk_statement FROM @drop_payments_order_fk;
EXECUTE drop_payments_order_fk_statement;
DEALLOCATE PREPARE drop_payments_order_fk_statement;
