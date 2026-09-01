-- SETTY 스키마. ddl-auto는 validate 고정이므로 스키마 변경은 이 파일에 SQL 추가로만 한다.
-- 매 부팅 시 실행되므로 모든 문장은 멱등이어야 한다 (CREATE TABLE IF NOT EXISTS, 컬럼 추가는 새 문장 대신 팀 합의 후 정리).

CREATE TABLE IF NOT EXISTS members (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    login_id     VARCHAR(20)  NOT NULL,
    password     VARCHAR(60)  NOT NULL,              -- BCrypt 해시 고정 60자
    role         VARCHAR(20)  NOT NULL,              -- MEMBER / ADMIN (ADMIN은 DB에서 직접 지정)
    phone_number VARCHAR(13)  NOT NULL,              -- 010-0000-0000
    address      VARCHAR(200) NOT NULL,
    token        VARCHAR(36)  NULL,                  -- 로그인 시 회전하는 UUID. 로그인 전 NULL
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_login_id (login_id),
    UNIQUE KEY uk_members_token (token)
);

-- role 체계 개편(PLATFORM/DRIVER → MEMBER) 마이그레이션. 멱등 — 이미 옮긴 행에는 no-op.
-- 기사 계정은 delivery_member로 분리(#190, #194)되어 DRIVER 행도 MEMBER로 흡수.
UPDATE members SET role = 'MEMBER' WHERE role IN ('PLATFORM', 'DRIVER');

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
    PRIMARY KEY (id),
    CONSTRAINT fk_listings_seller FOREIGN KEY (seller_id) REFERENCES members (id),
    CONSTRAINT chk_listings_price CHECK (price BETWEEN 0 AND 100000000),
    CONSTRAINT chk_listings_delivery_fee CHECK (delivery_fee IN (10000, 20000, 30000)),
    CONSTRAINT chk_listings_width CHECK (width_cm BETWEEN 1 AND 1000),
    CONSTRAINT chk_listings_depth CHECK (depth_cm BETWEEN 1 AND 1000),
    CONSTRAINT chk_listings_height CHECK (height_cm BETWEEN 1 AND 1000),
    INDEX idx_listings_public (deleted_at, sale_status, created_at, id),
    INDEX idx_listings_seller (seller_id, deleted_at, created_at, id)
);

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

CREATE TABLE IF NOT EXISTS delivery (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    order_id              BIGINT       NOT NULL,
    driver_id             BIGINT       NULL,
    item_name             VARCHAR(100) NOT NULL,
    category              VARCHAR(50)  NOT NULL,
    pickup_address        VARCHAR(255) NOT NULL,
    delivery_address      VARCHAR(255) NOT NULL,
    pickup_phone_number   VARCHAR(30)  NOT NULL,
    delivery_phone_number VARCHAR(30)  NOT NULL,
    estimated_fee         INT          NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    requested_at          TIMESTAMP(6) NOT NULL,
    accepted_at           TIMESTAMP(6) NULL,
    picked_up_at          TIMESTAMP(6) NULL,
    delivered_at          TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_order_id (order_id),
    CONSTRAINT chk_delivery_estimated_fee CHECK (estimated_fee >= 0)
);

CREATE TABLE IF NOT EXISTS delivery_member (
    id                           BIGINT       NOT NULL AUTO_INCREMENT,
    login_id                     VARCHAR(20)  NOT NULL,
    password                     VARCHAR(60)  NOT NULL,   -- BCrypt 해시 고정 60자
    phone_number                 VARCHAR(13)  NOT NULL,   -- 010-0000-0000
    license_plate_number         VARCHAR(20)  NOT NULL,   -- 00가0000
    car_type                     VARCHAR(30)  NOT NULL,   -- 다마스 등
    business_registration_number VARCHAR(12)  NOT NULL,   -- 000-00-00000
    token                        VARCHAR(36)  NULL,       -- 로그인 시 회전하는 UUID. 로그인 전 NULL
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_member_login_id (login_id),
    UNIQUE KEY uk_delivery_member_token (token)
);

-- 기존 DB에 orders가 이미 있다면 FK는 수동으로 1회 적용:
--   ALTER TABLE orders
--     ADD CONSTRAINT fk_orders_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
--     ADD CONSTRAINT fk_orders_buyer   FOREIGN KEY (buyer_id)   REFERENCES members (id);
CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id      BIGINT       NOT NULL,
    buyer_id        BIGINT       NOT NULL,
    delivery_status VARCHAR(20)  NOT NULL,              -- 플랫폼이 DeliveryStatusChanged 이벤트를 수신해 UPDATE
    driver_id       BIGINT       NULL,                  -- 현재 미사용, 실제 배정 기사는 delivery.driver_id에 저장
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_listing_id (listing_id),
    CONSTRAINT fk_orders_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_orders_buyer   FOREIGN KEY (buyer_id)   REFERENCES members (id)
);

-- 결제 (payment 계층). 토스페이먼츠 결제 결과(성공 DONE / 실패 ABORTED)를 1주문 1행으로 저장한다.
-- 주문은 결제 이전에 PENDING으로 먼저 생성되므로 payments.order_id는 항상 존재하는 주문을 가리킨다.
-- 실패 저장을 위해 payment_key·approved_at은 NULL 허용. 실패 후 재승인 시 같은 행을 DONE으로 전이한다.
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
    UNIQUE KEY uk_payments_toss_order_id (toss_order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

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
