-- 도입 전 주문의 실제 생성일은 알 수 없으므로 NULL로 남긴다.
ALTER TABLE orders ADD COLUMN created_at TIMESTAMP(6) NULL;
CREATE INDEX idx_orders_buyer_created_at ON orders (buyer_id, created_at, id);
