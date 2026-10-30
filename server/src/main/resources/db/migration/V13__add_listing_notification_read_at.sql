-- 알림함에서 읽었는지. acked_at(기기 수신 확인, 발송 측정값)과 따로 둔다. 적용 후에는 수정하지 말고 새 버전을 추가한다.
SET @listing_notification_read_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'listing_notifications' AND column_name = 'read_at'
);
SET @add_listing_notification_read_at = IF(
    @listing_notification_read_at_exists = 0,
    'ALTER TABLE listing_notifications ADD COLUMN read_at TIMESTAMP(6) NULL AFTER acked_at',
    'SELECT 1'
);
PREPARE add_listing_notification_read_at_statement FROM @add_listing_notification_read_at;
EXECUTE add_listing_notification_read_at_statement;
DEALLOCATE PREPARE add_listing_notification_read_at_statement;
