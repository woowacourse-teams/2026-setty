-- 배송 모듈(배송 요청·판정·기사 계정) 스키마.
-- 매 부팅 시 실행되므로 모든 문장은 멱등이어야 한다. 실행 순서는 application.yml의 spring.sql.init.schema-locations를 따른다.

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

-- 주문별 배송 판정. 배송 요청 등록과 구매자 취소 중 먼저 INSERT한 쪽이 결론을 정한다.
-- 취소가 먼저 판정된 주문은 재발행된 OrderConfirmed가 와도 배송 요청을 만들지 않는다.
CREATE TABLE IF NOT EXISTS delivery_order_decision (
    order_id   BIGINT       NOT NULL,
    outcome    VARCHAR(20)  NOT NULL,   -- REQUESTED / CANCELLED
    decided_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (order_id)
);

-- 판정 테이블 도입 전에 만들어진 배송 요청을 REQUESTED 판정으로 채운다. 이미 있는 판정은 건너뛴다.
INSERT IGNORE INTO delivery_order_decision (order_id, outcome, decided_at)
SELECT order_id, 'REQUESTED', requested_at FROM delivery;

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
