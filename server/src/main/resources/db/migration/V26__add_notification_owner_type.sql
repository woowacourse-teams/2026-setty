-- 기사(delivery_member)도 푸시 토큰과 알림을 받도록 토큰·주문 알림 행에 주인 종류를 둔다. 적용 후에는 수정하지 말고 새 버전을 추가한다.
-- 회원과 기사의 ID는 각자 1부터 늘어 겹치므로 member_id만으로는 주인을 가를 수 없다. member_id 칸은 주인 종류에 따라 회원 ID 또는 기사 ID를 담는다.
-- 배포 중 옛 버전이 같은 테이블을 쓰므로 칸 이름은 바꾸지 않고 더하기만 한다. 옛 버전이 넣는 행은 기본값으로 MEMBER가 된다.

-- 기사 ID는 members에 없으므로 회원 FK를 지운다. 회원 탈퇴 때 토큰 삭제는 알림 모듈의 탈퇴 리스너가 한다.
-- Flyway 편입 전 DB는 FK 이름이 다를 수 있어 이름 대신 참조 관계로 찾는다.
SET @device_tokens_member_fk = (
    SELECT constraint_name
    FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE()
      AND table_name = 'device_tokens'
      AND column_name = 'member_id'
      AND referenced_table_name = 'members'
    LIMIT 1
);
SET @drop_device_tokens_member_fk = IF(
    @device_tokens_member_fk IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE device_tokens DROP FOREIGN KEY `', @device_tokens_member_fk, '`')
);
PREPARE drop_device_tokens_member_fk_statement FROM @drop_device_tokens_member_fk;
EXECUTE drop_device_tokens_member_fk_statement;
DEALLOCATE PREPARE drop_device_tokens_member_fk_statement;

ALTER TABLE device_tokens
    ADD COLUMN owner_type VARCHAR(10) NOT NULL DEFAULT 'MEMBER' AFTER id,  -- MEMBER / DRIVER
    ADD INDEX idx_device_tokens_owner (owner_type, member_id);

SET @device_tokens_member_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'device_tokens'
      AND index_name = 'idx_device_tokens_member'
);
SET @drop_device_tokens_member_index = IF(
    @device_tokens_member_index_exists = 0,
    'SELECT 1',
    'ALTER TABLE device_tokens DROP INDEX idx_device_tokens_member'
);
PREPARE drop_device_tokens_member_index_statement FROM @drop_device_tokens_member_index;
EXECUTE drop_device_tokens_member_index_statement;
DEALLOCATE PREPARE drop_device_tokens_member_index_statement;

-- 기사 정산 알림에는 매물이 없어 listing_id를 비울 수 있게 한다. recipient에는 DRIVER가 더해진다.
ALTER TABLE order_notifications
    ADD COLUMN owner_type VARCHAR(10) NOT NULL DEFAULT 'MEMBER' AFTER listing_id,  -- MEMBER / DRIVER
    MODIFY COLUMN listing_id BIGINT NULL,
    ADD CONSTRAINT uk_order_notifications_owner_event UNIQUE (order_id, owner_type, member_id, kind, occurred_at),
    ADD INDEX idx_order_notifications_owner (owner_type, member_id, created_at, id),
    DROP INDEX uk_order_notifications_event,
    DROP INDEX idx_order_notifications_member;
