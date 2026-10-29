-- 키워드 조건 중 배송비 상한과 총가격(물품가+배송비) 상한. 적용 후에는 수정하지 말고 새 버전을 추가한다.
-- 배송비는 매물과 구독자 사이 거리·부피로 정해지므로 이 두 조건은 SQL이 아니라 서버가 구독자마다 계산해 판정한다. NULL이면 조건 없음.
SET @keyword_max_delivery_fee_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_subscriptions' AND column_name = 'max_delivery_fee'
);
SET @add_keyword_max_delivery_fee = IF(
    @keyword_max_delivery_fee_exists = 0,
    'ALTER TABLE keyword_subscriptions ADD COLUMN max_delivery_fee INT NULL AFTER max_price',
    'SELECT 1'
);
PREPARE add_keyword_max_delivery_fee_statement FROM @add_keyword_max_delivery_fee;
EXECUTE add_keyword_max_delivery_fee_statement;
DEALLOCATE PREPARE add_keyword_max_delivery_fee_statement;

SET @keyword_max_total_price_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_subscriptions' AND column_name = 'max_total_price'
);
SET @add_keyword_max_total_price = IF(
    @keyword_max_total_price_exists = 0,
    'ALTER TABLE keyword_subscriptions ADD COLUMN max_total_price INT NULL AFTER max_delivery_fee',
    'SELECT 1'
);
PREPARE add_keyword_max_total_price_statement FROM @add_keyword_max_total_price;
EXECUTE add_keyword_max_total_price_statement;
DEALLOCATE PREPARE add_keyword_max_total_price_statement;
