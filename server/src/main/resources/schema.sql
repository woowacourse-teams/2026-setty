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
