-- SETTY 스키마. ddl-auto는 validate 고정이므로 스키마 변경은 이 파일에 SQL 추가로만 한다.
-- 매 부팅 시 실행되므로 모든 문장은 멱등이어야 한다 (CREATE TABLE IF NOT EXISTS, 컬럼 추가는 새 문장 대신 팀 합의 후 정리).

CREATE TABLE IF NOT EXISTS members (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    login_id     VARCHAR(20)  NOT NULL,
    password     VARCHAR(60)  NOT NULL,              -- BCrypt 해시 고정 60자
    role         VARCHAR(20)  NOT NULL,              -- PLATFORM / DRIVER
    phone_number VARCHAR(13)  NOT NULL,              -- 010-0000-0000
    address      VARCHAR(200) NOT NULL,
    token        VARCHAR(36)  NULL,                  -- 로그인 시 회전하는 UUID. 로그인 전 NULL
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_login_id (login_id),
    UNIQUE KEY uk_members_token (token)
);

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
