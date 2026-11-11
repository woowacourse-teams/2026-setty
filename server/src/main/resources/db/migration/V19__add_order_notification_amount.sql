-- 정산 확정 알림(kind = SETTLEMENT_CONFIRMED)에 싣는 정산 금액. 다른 종류의 알림은 NULL
ALTER TABLE order_notifications
    ADD COLUMN amount INT NULL AFTER kind;
