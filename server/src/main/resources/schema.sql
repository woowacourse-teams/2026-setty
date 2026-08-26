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

CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id      BIGINT       NOT NULL,
    buyer_id        BIGINT       NOT NULL,
    delivery_status VARCHAR(20)  NOT NULL,              -- 배송 팀만 UPDATE (DEC-10)
    driver_id       BIGINT       NULL,                  -- 배송 팀만 UPDATE, 수락 전 NULL
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_listing_id (listing_id)
);
