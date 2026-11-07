-- 토스 승인 전에 시도를 UNKNOWN으로 먼저 남겨, 응답을 못 받은 결제도 paymentKey로 다시 조회할 수 있게 한다.
-- 상태: UNKNOWN(승인 결과 모름) / DONE(승인) / CANCELED(환불 완료) / ABORTED(레거시, 새로 저장하지 않음)
ALTER TABLE payments
    ADD COLUMN attempted_at TIMESTAMP(6) NULL AFTER approved_at,  -- 마지막 승인 시도 시각. 도입 전 행은 NULL
    ADD INDEX idx_payments_unknown (status, attempted_at);

-- 끝난 주문(만료·판매자 취소)에 늦게 승인된 결제의 환불. 결제당 1행이고 배치가 토스 결제 취소를 보낸다.
-- 주문은 payments.order_id로 찾는다(같은 값을 두 곳에 두지 않는다).
CREATE TABLE IF NOT EXISTS payment_refunds (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id       BIGINT       NOT NULL,
    reason           VARCHAR(20)  NOT NULL,              -- ORDER_EXPIRED / ORDER_CANCELLED. 토스 cancelReason 문구를 정한다
    status           VARCHAR(10)  NOT NULL,              -- REQUESTED / DONE / FAILED
    idempotency_key  VARCHAR(64)  NOT NULL,              -- 토스 취소 Idempotency-Key. 재시도에도 같은 값을 쓴다(15일 유효)
    attempts         INT          NOT NULL DEFAULT 0,    -- 토스 취소 호출 횟수
    last_error_code  VARCHAR(60)  NULL,                  -- 마지막 실패의 토스 에러 코드 또는 TIMEOUT 등
    transaction_key  VARCHAR(64)  NULL,                  -- 토스 취소 거래 키
    canceled_at      TIMESTAMP(6) NULL,                  -- 토스가 응답한 취소 시각
    requested_at     TIMESTAMP(6) NOT NULL,
    completed_at     TIMESTAMP(6) NULL,                  -- DONE·FAILED로 끝난 시각
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_refunds_payment UNIQUE (payment_id),
    CONSTRAINT fk_payment_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    INDEX idx_payment_refunds_status (status, id)
);
