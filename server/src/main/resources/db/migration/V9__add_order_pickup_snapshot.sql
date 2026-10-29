-- 주문 시점 출발지와 배송 요청의 출발·도착 구역. 적용 후에는 수정하지 말고 새 버전을 추가한다.

-- orders: 출발지(판매자) 주소·연락처와 출발·도착 표준 값. 행정동·우편번호는 배송비 구역(매물·구매자), 주소·관리번호는 실제 방문지다.
-- 도입 전 주문은 NULL이고, 배송 요청을 만들 때 지금 회원 정보로 대신한다.
-- delivery: 배송원 추천이 거리표를 읽을 출발·도착 행정동과 우편번호. 도입 전 배송 요청은 NULL이다.

SET @orders_pickup_address_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'pickup_address'
);
SET @add_orders_pickup_address = IF(
    @orders_pickup_address_exists = 0,
    'ALTER TABLE orders ADD COLUMN pickup_address VARCHAR(200) NULL AFTER delivery_phone_number',
    'SELECT 1'
);
PREPARE add_orders_pickup_address_statement FROM @add_orders_pickup_address;
EXECUTE add_orders_pickup_address_statement;
DEALLOCATE PREPARE add_orders_pickup_address_statement;

SET @orders_pickup_phone_number_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'pickup_phone_number'
);
SET @add_orders_pickup_phone_number = IF(
    @orders_pickup_phone_number_exists = 0,
    'ALTER TABLE orders ADD COLUMN pickup_phone_number VARCHAR(13) NULL AFTER pickup_address',
    'SELECT 1'
);
PREPARE add_orders_pickup_phone_number_statement FROM @add_orders_pickup_phone_number;
EXECUTE add_orders_pickup_phone_number_statement;
DEALLOCATE PREPARE add_orders_pickup_phone_number_statement;

SET @orders_pickup_dong_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'pickup_dong_code'
);
SET @add_orders_pickup_dong_code = IF(
    @orders_pickup_dong_code_exists = 0,
    'ALTER TABLE orders ADD COLUMN pickup_dong_code VARCHAR(10) NULL AFTER pickup_phone_number',
    'SELECT 1'
);
PREPARE add_orders_pickup_dong_code_statement FROM @add_orders_pickup_dong_code;
EXECUTE add_orders_pickup_dong_code_statement;
DEALLOCATE PREPARE add_orders_pickup_dong_code_statement;

SET @orders_pickup_zip_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'pickup_zip_code'
);
SET @add_orders_pickup_zip_code = IF(
    @orders_pickup_zip_code_exists = 0,
    'ALTER TABLE orders ADD COLUMN pickup_zip_code CHAR(5) NULL AFTER pickup_dong_code',
    'SELECT 1'
);
PREPARE add_orders_pickup_zip_code_statement FROM @add_orders_pickup_zip_code;
EXECUTE add_orders_pickup_zip_code_statement;
DEALLOCATE PREPARE add_orders_pickup_zip_code_statement;

SET @orders_pickup_address_mgmt_no_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'pickup_address_mgmt_no'
);
SET @add_orders_pickup_address_mgmt_no = IF(
    @orders_pickup_address_mgmt_no_exists = 0,
    'ALTER TABLE orders ADD COLUMN pickup_address_mgmt_no CHAR(25) NULL AFTER pickup_zip_code',
    'SELECT 1'
);
PREPARE add_orders_pickup_address_mgmt_no_statement FROM @add_orders_pickup_address_mgmt_no;
EXECUTE add_orders_pickup_address_mgmt_no_statement;
DEALLOCATE PREPARE add_orders_pickup_address_mgmt_no_statement;

SET @orders_delivery_zip_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'delivery_zip_code'
);
SET @add_orders_delivery_zip_code = IF(
    @orders_delivery_zip_code_exists = 0,
    'ALTER TABLE orders ADD COLUMN delivery_zip_code CHAR(5) NULL AFTER pickup_address_mgmt_no',
    'SELECT 1'
);
PREPARE add_orders_delivery_zip_code_statement FROM @add_orders_delivery_zip_code;
EXECUTE add_orders_delivery_zip_code_statement;
DEALLOCATE PREPARE add_orders_delivery_zip_code_statement;

SET @orders_delivery_address_mgmt_no_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'delivery_address_mgmt_no'
);
SET @add_orders_delivery_address_mgmt_no = IF(
    @orders_delivery_address_mgmt_no_exists = 0,
    'ALTER TABLE orders ADD COLUMN delivery_address_mgmt_no CHAR(25) NULL AFTER delivery_zip_code',
    'SELECT 1'
);
PREPARE add_orders_delivery_address_mgmt_no_statement FROM @add_orders_delivery_address_mgmt_no;
EXECUTE add_orders_delivery_address_mgmt_no_statement;
DEALLOCATE PREPARE add_orders_delivery_address_mgmt_no_statement;

SET @delivery_pickup_dong_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'delivery' AND column_name = 'pickup_dong_code'
);
SET @add_delivery_pickup_dong_code = IF(
    @delivery_pickup_dong_code_exists = 0,
    'ALTER TABLE delivery ADD COLUMN pickup_dong_code VARCHAR(10) NULL AFTER delivery_phone_number',
    'SELECT 1'
);
PREPARE add_delivery_pickup_dong_code_statement FROM @add_delivery_pickup_dong_code;
EXECUTE add_delivery_pickup_dong_code_statement;
DEALLOCATE PREPARE add_delivery_pickup_dong_code_statement;

SET @delivery_pickup_zip_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'delivery' AND column_name = 'pickup_zip_code'
);
SET @add_delivery_pickup_zip_code = IF(
    @delivery_pickup_zip_code_exists = 0,
    'ALTER TABLE delivery ADD COLUMN pickup_zip_code CHAR(5) NULL AFTER pickup_dong_code',
    'SELECT 1'
);
PREPARE add_delivery_pickup_zip_code_statement FROM @add_delivery_pickup_zip_code;
EXECUTE add_delivery_pickup_zip_code_statement;
DEALLOCATE PREPARE add_delivery_pickup_zip_code_statement;

SET @delivery_delivery_dong_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'delivery' AND column_name = 'delivery_dong_code'
);
SET @add_delivery_delivery_dong_code = IF(
    @delivery_delivery_dong_code_exists = 0,
    'ALTER TABLE delivery ADD COLUMN delivery_dong_code VARCHAR(10) NULL AFTER pickup_zip_code',
    'SELECT 1'
);
PREPARE add_delivery_delivery_dong_code_statement FROM @add_delivery_delivery_dong_code;
EXECUTE add_delivery_delivery_dong_code_statement;
DEALLOCATE PREPARE add_delivery_delivery_dong_code_statement;

SET @delivery_delivery_zip_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'delivery' AND column_name = 'delivery_zip_code'
);
SET @add_delivery_delivery_zip_code = IF(
    @delivery_delivery_zip_code_exists = 0,
    'ALTER TABLE delivery ADD COLUMN delivery_zip_code CHAR(5) NULL AFTER delivery_dong_code',
    'SELECT 1'
);
PREPARE add_delivery_delivery_zip_code_statement FROM @add_delivery_delivery_zip_code;
EXECUTE add_delivery_delivery_zip_code_statement;
DEALLOCATE PREPARE add_delivery_delivery_zip_code_statement;
