-- 알림 측정 종료 시에만 사용. seed-subscribers와 같은 dev 접속을 확인하고 새 mysql 배치 연결에서 실행한다.
-- --init-command로 @fixture_expected_database, @fixture_expected_hostname을 전달한다.
-- 기본은 ROLLBACK. 대상과 건수를 확인한 후에만 마지막을 COMMIT으로 바꾼다.
-- 측정 중 등록된 매물·사진 행과 알림도 함께 지운다. S3에 올라간 사진 객체는 지우지 않는다.

CREATE TEMPORARY TABLE fixture_assertion (
    label VARCHAR(100) NOT NULL,
    ok INT NOT NULL,
    CONSTRAINT fixture_assertion_must_pass CHECK (ok = 1)
);
INSERT INTO fixture_assertion VALUES ('confirmed_database',
    IF(@fixture_expected_database <> '' AND DATABASE() = @fixture_expected_database, 1, 0));
INSERT INTO fixture_assertion VALUES ('confirmed_hostname',
    IF(@fixture_expected_hostname <> '' AND @@hostname = @fixture_expected_hostname, 1, 0));
INSERT INTO fixture_assertion VALUES ('foreign_keys_enabled', @@foreign_key_checks);

START TRANSACTION;
SET @fixture_seller_id = (
    SELECT id FROM members WHERE login_id = 's3-seller-001'
      AND address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY' AND password = REPEAT('!', 60)
);
INSERT INTO fixture_assertion VALUES ('fixture_seller_matches', IF(@fixture_seller_id IS NOT NULL, 1, 0));
INSERT INTO fixture_assertion
SELECT 'no_orders_on_fixture_listings', IF(COUNT(*) = 0, 1, 0)
FROM orders WHERE listing_id IN (SELECT id FROM listings WHERE seller_id = @fixture_seller_id);
INSERT INTO fixture_assertion
SELECT 'no_favorites_on_fixture_listings', IF(COUNT(*) = 0, 1, 0)
FROM favorites WHERE listing_id IN (SELECT id FROM listings WHERE seller_id = @fixture_seller_id);

SELECT COUNT(*) AS notifications_to_delete FROM listing_notifications
WHERE listing_id IN (SELECT id FROM listings WHERE seller_id = @fixture_seller_id)
   OR member_id IN (SELECT id FROM members WHERE login_id LIKE 's3-sub-%' AND address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY');
SELECT COUNT(*) AS subscriptions_to_delete FROM keyword_subscriptions
WHERE member_id IN (SELECT id FROM members WHERE login_id LIKE 's3-sub-%' AND address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY');
SELECT COUNT(*) AS listings_to_delete FROM listings WHERE seller_id = @fixture_seller_id;
SELECT COUNT(*) AS members_to_delete FROM members
WHERE address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY' AND (login_id = 's3-seller-001' OR login_id LIKE 's3-sub-%');

DELETE FROM listing_notifications
WHERE listing_id IN (SELECT id FROM listings WHERE seller_id = @fixture_seller_id)
   OR member_id IN (SELECT id FROM members WHERE login_id LIKE 's3-sub-%' AND address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY');
DELETE FROM keyword_subscriptions
WHERE member_id IN (SELECT id FROM members WHERE login_id LIKE 's3-sub-%' AND address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY');
DELETE FROM listing_images WHERE listing_id IN (SELECT id FROM listings WHERE seller_id = @fixture_seller_id);
DELETE FROM listings WHERE seller_id = @fixture_seller_id;
DELETE FROM members
WHERE address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY' AND (login_id = 's3-seller-001' OR login_id LIKE 's3-sub-%');

SELECT COUNT(*) AS fixture_members_after_run FROM members
WHERE login_id = 's3-seller-001' OR login_id LIKE 's3-sub-%';
SELECT COUNT(*) AS fixture_subscriptions_after_run FROM keyword_subscriptions WHERE keyword = 's3-notify';

ROLLBACK;
