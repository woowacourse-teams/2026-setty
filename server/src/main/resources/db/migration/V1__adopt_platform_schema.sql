-- 플랫폼 모듈(회원·매물·주문·찜) 스키마. 다른 모듈 파일보다 먼저 실행한다(테이블 간 FK가 이 파일 안에 있음).
-- Flyway 최초 편입용: 빈 DB와 기존 DB 모두 지원한다. 적용 후에는 수정하지 말고 새 버전을 추가한다.

CREATE TABLE IF NOT EXISTS members (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    login_id     VARCHAR(20)  NOT NULL,
    password     VARCHAR(60)  NOT NULL,              -- BCrypt 해시 고정 60자
    role         VARCHAR(20)  NOT NULL,              -- MEMBER / ADMIN (ADMIN은 DB에서 직접 지정)
    phone_number VARCHAR(13)  NOT NULL,              -- 010-0000-0000
    address      VARCHAR(200) NOT NULL,
    dong_code    VARCHAR(10)  NULL,                  -- 서울 행정동 코드(deliveryfee/seoul-dongs.csv). 도입 전 가입자는 NULL
    token        VARCHAR(36)  NULL,                  -- 로그인 시 회전하는 UUID. 로그인 전 NULL
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_login_id (login_id),
    UNIQUE KEY uk_members_token (token)
);

-- role 체계 개편(PLATFORM/DRIVER → MEMBER) 마이그레이션. 멱등 — 이미 옮긴 행에는 no-op.
-- 기사 계정은 delivery_member로 분리(#190, #194)되어 DRIVER 행도 MEMBER로 흡수.
UPDATE members SET role = 'MEMBER' WHERE role IN ('PLATFORM', 'DRIVER');

SET @members_dong_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'dong_code'
);
SET @add_members_dong_code = IF(
    @members_dong_code_exists = 0,
    'ALTER TABLE members ADD COLUMN dong_code VARCHAR(10) NULL AFTER address',
    'SELECT 1'
);
PREPARE add_members_dong_code_statement FROM @add_members_dong_code;
EXECUTE add_members_dong_code_statement;
DEALLOCATE PREPARE add_members_dong_code_statement;

CREATE TABLE IF NOT EXISTS listings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    price INT NOT NULL,
    delivery_fee INT NOT NULL,
    category VARCHAR(20) NOT NULL,
    condition_grade VARCHAR(1) NOT NULL,
    width_cm INT NOT NULL,
    depth_cm INT NOT NULL,
    height_cm INT NOT NULL,
    sale_status VARCHAR(20) NOT NULL,
    has_purchase_request BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    dong_code VARCHAR(10) NULL,
    volume_tier VARCHAR(10) GENERATED ALWAYS AS (
        CASE WHEN width_cm * depth_cm * height_cm <= 300000 THEN 'SMALL'
             WHEN width_cm * depth_cm * height_cm <= 1000000 THEN 'MEDIUM'
             ELSE 'LARGE' END
    ) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_listings_seller FOREIGN KEY (seller_id) REFERENCES members (id),
    CONSTRAINT chk_listings_price CHECK (price BETWEEN 0 AND 100000000),
    CONSTRAINT chk_listings_delivery_fee CHECK (delivery_fee IN (10000, 20000, 30000)),
    CONSTRAINT chk_listings_width CHECK (width_cm BETWEEN 1 AND 1000),
    CONSTRAINT chk_listings_depth CHECK (depth_cm BETWEEN 1 AND 1000),
    CONSTRAINT chk_listings_height CHECK (height_cm BETWEEN 1 AND 1000),
    INDEX idx_listings_seller (seller_id, deleted_at, created_at, id),
    INDEX idx_listings_delivery_filter (deleted_at, sale_status, created_at, id, dong_code, volume_tier)
);

-- 배송비 예산 검색용. dong_code는 판매자 행정동을 복사하고, volume_tier는 DeliveryFeePolicy와 같은 부피 경계로 계산한다.
-- 색인 끝에 두 컬럼을 두어 최신순으로 읽으면서 색인 안에서 (행정동, 부피) 조건을 거른다.
SET @listings_dong_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'listings' AND column_name = 'dong_code'
);
SET @add_listings_dong_code = IF(
    @listings_dong_code_exists = 0,
    'ALTER TABLE listings ADD COLUMN dong_code VARCHAR(10) NULL AFTER deleted_at',
    'SELECT 1'
);
PREPARE add_listings_dong_code_statement FROM @add_listings_dong_code;
EXECUTE add_listings_dong_code_statement;
DEALLOCATE PREPARE add_listings_dong_code_statement;

SET @listings_volume_tier_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'listings' AND column_name = 'volume_tier'
);
SET @add_listings_volume_tier = IF(
    @listings_volume_tier_exists = 0,
    'ALTER TABLE listings ADD COLUMN volume_tier VARCHAR(10) GENERATED ALWAYS AS (CASE WHEN width_cm * depth_cm * height_cm <= 300000 THEN ''SMALL'' WHEN width_cm * depth_cm * height_cm <= 1000000 THEN ''MEDIUM'' ELSE ''LARGE'' END) STORED AFTER dong_code',
    'SELECT 1'
);
PREPARE add_listings_volume_tier_statement FROM @add_listings_volume_tier;
EXECUTE add_listings_volume_tier_statement;
DEALLOCATE PREPARE add_listings_volume_tier_statement;

SET @listings_delivery_filter_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'listings' AND index_name = 'idx_listings_delivery_filter'
);
SET @add_listings_delivery_filter_index = IF(
    @listings_delivery_filter_index_exists = 0,
    'ALTER TABLE listings ADD INDEX idx_listings_delivery_filter (deleted_at, sale_status, created_at, id, dong_code, volume_tier)',
    'SELECT 1'
);
PREPARE add_listings_delivery_filter_index_statement FROM @add_listings_delivery_filter_index;
EXECUTE add_listings_delivery_filter_index_statement;
DEALLOCATE PREPARE add_listings_delivery_filter_index_statement;

-- idx_listings_delivery_filter의 앞 네 컬럼과 같아 일반 목록도 그 색인으로 같은 순서로 읽으므로 지운다.
SET @listings_public_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'listings' AND index_name = 'idx_listings_public'
);
SET @drop_listings_public_index = IF(
    @listings_public_index_exists > 0,
    'ALTER TABLE listings DROP INDEX idx_listings_public',
    'SELECT 1'
);
PREPARE drop_listings_public_index_statement FROM @drop_listings_public_index;
EXECUTE drop_listings_public_index_statement;
DEALLOCATE PREPARE drop_listings_public_index_statement;

CREATE TABLE IF NOT EXISTS listing_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_listing_images_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT uk_listing_images_order UNIQUE (listing_id, display_order),
    INDEX idx_listing_images_listing (listing_id, display_order)
);

-- 기존 DB에 orders가 이미 있다면 FK는 수동으로 1회 적용:
--   ALTER TABLE orders
--     ADD CONSTRAINT fk_orders_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
--     ADD CONSTRAINT fk_orders_buyer   FOREIGN KEY (buyer_id)   REFERENCES members (id);
CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id      BIGINT       NOT NULL,
    buyer_id        BIGINT       NOT NULL,
    delivery_status VARCHAR(20)  NOT NULL,              -- 플랫폼이 배송 상태 이벤트를 수신해 UPDATE
    order_status    VARCHAR(20)  NOT NULL,              -- 주문의 결제 및 취소 상태
    cancellation_request_id VARCHAR(36) NULL,           -- 현재 취소 SAGA 요청 식별자
    driver_id       BIGINT       NULL,                  -- 현재 미사용, 실제 배정 기사는 delivery.driver_id에 저장
    pending_expires_at TIMESTAMP(6) NULL,                -- PENDING 주문 자동 만료 시각
    delivery_status_changed_at TIMESTAMP(6) NULL,        -- 배송 이벤트로 동기화된 배송 상태 변경 시각
    delivery_fee    INT          NULL,                  -- 주문 시점 구매자 기준 배송비. 도입 전 주문은 NULL(매물 배송비로 대체)
    delivery_dong_code VARCHAR(10) NULL,                -- 주문 시점 배송지 행정동
    delivery_address VARCHAR(200) NULL,                 -- 주문 시점 배송지. 도입 전 주문은 NULL(회원 주소로 대체)
    delivery_phone_number VARCHAR(13) NULL,             -- 주문 시점 구매자 연락처
    active_listing_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN order_status IN ('EXPIRED', 'CANCELLED') THEN NULL ELSE listing_id END
    ) STORED,
    PRIMARY KEY (id),
    INDEX idx_orders_listing_id (listing_id),
    UNIQUE KEY uk_orders_active_listing_id (active_listing_id),
    CONSTRAINT fk_orders_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_orders_buyer   FOREIGN KEY (buyer_id)   REFERENCES members (id),
    INDEX idx_orders_pending_expiration (delivery_status, pending_expires_at),
    INDEX idx_orders_order_expiration (order_status, pending_expires_at),
    INDEX idx_orders_completion_due (order_status, delivery_status, delivery_status_changed_at)
);

-- 기존 orders 테이블 마이그레이션. MySQL은 ADD COLUMN/INDEX IF NOT EXISTS를 지원하지 않으므로
-- information_schema 확인 결과에 따라 동적 DDL을 실행한다.
SET @orders_pending_expires_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND column_name = 'pending_expires_at'
);
SET @add_orders_pending_expires_at = IF(
    @orders_pending_expires_at_exists = 0,
    'ALTER TABLE orders ADD COLUMN pending_expires_at TIMESTAMP(6) NULL AFTER driver_id',
    'SELECT 1'
);
PREPARE add_orders_pending_expires_at_statement FROM @add_orders_pending_expires_at;
EXECUTE add_orders_pending_expires_at_statement;
DEALLOCATE PREPARE add_orders_pending_expires_at_statement;

-- 생성 시각을 알 수 없는 기존 PENDING 주문에는 배포 시점부터 기본 6분을 부여한다.
UPDATE orders
SET pending_expires_at = CURRENT_TIMESTAMP(6) + INTERVAL 6 MINUTE
WHERE delivery_status = 'PENDING'
  AND pending_expires_at IS NULL;

SET @orders_pending_expiration_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND index_name = 'idx_orders_pending_expiration'
);
SET @add_orders_pending_expiration_index = IF(
    @orders_pending_expiration_index_exists = 0,
    'ALTER TABLE orders ADD INDEX idx_orders_pending_expiration (delivery_status, pending_expires_at)',
    'SELECT 1'
);
PREPARE add_orders_pending_expiration_index_statement FROM @add_orders_pending_expiration_index;
EXECUTE add_orders_pending_expiration_index_statement;
DEALLOCATE PREPARE add_orders_pending_expiration_index_statement;

-- 기존 주문은 배송 상태를 기준으로 주문 상태를 1회 채운다.
SET @orders_order_status_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND column_name = 'order_status'
);
SET @add_orders_order_status = IF(
    @orders_order_status_exists = 0,
    'ALTER TABLE orders ADD COLUMN order_status VARCHAR(20) NULL AFTER delivery_status',
    'SELECT 1'
);
PREPARE add_orders_order_status_statement FROM @add_orders_order_status;
EXECUTE add_orders_order_status_statement;
DEALLOCATE PREPARE add_orders_order_status_statement;

UPDATE orders
SET order_status = CASE WHEN delivery_status = 'PENDING' THEN 'PENDING' ELSE 'CONFIRMED' END
WHERE order_status IS NULL;

SET @orders_order_status_nullable = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND column_name = 'order_status'
      AND is_nullable = 'YES'
);
SET @require_orders_order_status = IF(
    @orders_order_status_nullable > 0,
    'ALTER TABLE orders MODIFY COLUMN order_status VARCHAR(20) NOT NULL',
    'SELECT 1'
);
PREPARE require_orders_order_status_statement FROM @require_orders_order_status;
EXECUTE require_orders_order_status_statement;
DEALLOCATE PREPARE require_orders_order_status_statement;

SET @orders_cancellation_request_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND column_name = 'cancellation_request_id'
);
SET @add_orders_cancellation_request_id = IF(
    @orders_cancellation_request_id_exists = 0,
    'ALTER TABLE orders ADD COLUMN cancellation_request_id VARCHAR(36) NULL AFTER order_status',
    'SELECT 1'
);
PREPARE add_orders_cancellation_request_id_statement FROM @add_orders_cancellation_request_id;
EXECUTE add_orders_cancellation_request_id_statement;
DEALLOCATE PREPARE add_orders_cancellation_request_id_statement;

SET @orders_order_expiration_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND index_name = 'idx_orders_order_expiration'
);
SET @add_orders_order_expiration_index = IF(
    @orders_order_expiration_index_exists = 0,
    'ALTER TABLE orders ADD INDEX idx_orders_order_expiration (order_status, pending_expires_at)',
    'SELECT 1'
);
PREPARE add_orders_order_expiration_index_statement FROM @add_orders_order_expiration_index;
EXECUTE add_orders_order_expiration_index_statement;
DEALLOCATE PREPARE add_orders_order_expiration_index_statement;

SET @orders_delivery_status_changed_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND column_name = 'delivery_status_changed_at'
);
SET @add_orders_delivery_status_changed_at = IF(
    @orders_delivery_status_changed_at_exists = 0,
    'ALTER TABLE orders ADD COLUMN delivery_status_changed_at TIMESTAMP(6) NULL AFTER pending_expires_at, ADD INDEX idx_orders_completion_due (order_status, delivery_status, delivery_status_changed_at)',
    'SELECT 1'
);
PREPARE add_orders_delivery_status_changed_at_statement FROM @add_orders_delivery_status_changed_at;
EXECUTE add_orders_delivery_status_changed_at_statement;
DEALLOCATE PREPARE add_orders_delivery_status_changed_at_statement;

-- 주문 시점 배송비·배송지 스냅샷(docs/policy/delivery.md "요청·정보 확정")
-- 수동 반영이 일부만 되었어도 빠진 컬럼을 각각 추가한다. 기존 값은 변경하지 않는다.
SET @orders_delivery_fee_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'delivery_fee'
);
SET @add_orders_delivery_fee = IF(
    @orders_delivery_fee_exists = 0,
    'ALTER TABLE orders ADD COLUMN delivery_fee INT NULL AFTER delivery_status_changed_at',
    'SELECT 1'
);
PREPARE add_orders_delivery_fee_statement FROM @add_orders_delivery_fee;
EXECUTE add_orders_delivery_fee_statement;
DEALLOCATE PREPARE add_orders_delivery_fee_statement;

SET @orders_delivery_dong_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'delivery_dong_code'
);
SET @add_orders_delivery_dong_code = IF(
    @orders_delivery_dong_code_exists = 0,
    'ALTER TABLE orders ADD COLUMN delivery_dong_code VARCHAR(10) NULL AFTER delivery_fee',
    'SELECT 1'
);
PREPARE add_orders_delivery_dong_code_statement FROM @add_orders_delivery_dong_code;
EXECUTE add_orders_delivery_dong_code_statement;
DEALLOCATE PREPARE add_orders_delivery_dong_code_statement;

SET @orders_delivery_address_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'delivery_address'
);
SET @add_orders_delivery_address = IF(
    @orders_delivery_address_exists = 0,
    'ALTER TABLE orders ADD COLUMN delivery_address VARCHAR(200) NULL AFTER delivery_dong_code',
    'SELECT 1'
);
PREPARE add_orders_delivery_address_statement FROM @add_orders_delivery_address;
EXECUTE add_orders_delivery_address_statement;
DEALLOCATE PREPARE add_orders_delivery_address_statement;

SET @orders_delivery_phone_number_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'delivery_phone_number'
);
SET @add_orders_delivery_phone_number = IF(
    @orders_delivery_phone_number_exists = 0,
    'ALTER TABLE orders ADD COLUMN delivery_phone_number VARCHAR(13) NULL AFTER delivery_address',
    'SELECT 1'
);
PREPARE add_orders_delivery_phone_number_statement FROM @add_orders_delivery_phone_number;
EXECUTE add_orders_delivery_phone_number_statement;
DEALLOCATE PREPARE add_orders_delivery_phone_number_statement;

-- 판매 완료 확정 도입 전에 배송 완료된 주문은 변경 시각을 알 수 없어 배포 시점으로 채운다. 3일 뒤 자동 판매 완료된다.
UPDATE orders
SET delivery_status_changed_at = CURRENT_TIMESTAMP(6)
WHERE order_status = 'CONFIRMED'
  AND delivery_status = 'DELIVERED'
  AND delivery_status_changed_at IS NULL;

-- 종료된 주문은 보존하면서 같은 매물의 새 주문을 허용한다.
-- MySQL UNIQUE는 NULL 중복을 허용하므로 활성 주문의 listing_id만 유일하게 제한한다.
SET @orders_active_listing_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'active_listing_id'
);
SET @add_orders_active_listing_column = IF(
    @orders_active_listing_column_exists = 0,
    'ALTER TABLE orders ADD COLUMN active_listing_id BIGINT GENERATED ALWAYS AS (CASE WHEN order_status IN (''EXPIRED'', ''CANCELLED'') THEN NULL ELSE listing_id END) STORED',
    'SELECT 1'
);
PREPARE add_orders_active_listing_column_statement FROM @add_orders_active_listing_column;
EXECUTE add_orders_active_listing_column_statement;
DEALLOCATE PREPARE add_orders_active_listing_column_statement;

SET @orders_active_listing_unique_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'uk_orders_active_listing_id'
);
SET @add_orders_active_listing_unique = IF(
    @orders_active_listing_unique_exists = 0,
    'ALTER TABLE orders ADD UNIQUE KEY uk_orders_active_listing_id (active_listing_id)',
    'SELECT 1'
);
PREPARE add_orders_active_listing_unique_statement FROM @add_orders_active_listing_unique;
EXECUTE add_orders_active_listing_unique_statement;
DEALLOCATE PREPARE add_orders_active_listing_unique_statement;

SET @orders_listing_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_listing_id'
);
SET @add_orders_listing_index = IF(
    @orders_listing_index_exists = 0,
    'ALTER TABLE orders ADD INDEX idx_orders_listing_id (listing_id)',
    'SELECT 1'
);
PREPARE add_orders_listing_index_statement FROM @add_orders_listing_index;
EXECUTE add_orders_listing_index_statement;
DEALLOCATE PREPARE add_orders_listing_index_statement;

SET @orders_listing_unique_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'uk_orders_listing_id'
);
SET @drop_orders_listing_unique = IF(
    @orders_listing_unique_exists > 0,
    'ALTER TABLE orders DROP INDEX uk_orders_listing_id',
    'SELECT 1'
);
PREPARE drop_orders_listing_unique_statement FROM @drop_orders_listing_unique;
EXECUTE drop_orders_listing_unique_statement;
DEALLOCATE PREPARE drop_orders_listing_unique_statement;

CREATE TABLE IF NOT EXISTS favorites (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    listing_id BIGINT       NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_favorites_member_listing UNIQUE (member_id, listing_id),
    CONSTRAINT fk_favorites_member  FOREIGN KEY (member_id)  REFERENCES members (id),
    CONSTRAINT fk_favorites_listing FOREIGN KEY (listing_id) REFERENCES listings (id)
);
