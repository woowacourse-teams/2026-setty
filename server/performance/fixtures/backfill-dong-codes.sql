-- dev 1회성: 행정동 도입 전 데이터의 dong_code를 채운다.
-- 1) 알림 측정 판매자 s3-seller-001에 행정동 1111053000(종로구 사직동)을 넣는다(비어 있을 때만).
-- 2) 판매자 회원에 행정동이 있으면 그 값으로 매물 dong_code를 채운다. 판매자 행정동이 없으면 그대로 둔다.
-- MySQL 8.0.16+ / InnoDB. --init-command로 @fixture_expected_database, @fixture_expected_hostname 전달.
-- --skip-reconnect --skip-force 필수. 기본은 마지막 ROLLBACK, 시험 통과 후 임시 실행본만 COMMIT.
-- 부하 측정 중에는 실행하지 않는다. 읽은 listings·members 행을 COMMIT까지 잠가 로그인 토큰 회전과 매물 잠금이 잠깐 막힌다.
-- 실제 실행 출력의 updated_s3_seller와 backfilled_listing_ids를 보관한다. 되돌릴 때 revert-dong-backfill.sql에 넘긴다.

SET SESSION time_zone = '+00:00';

CREATE TEMPORARY TABLE fixture_assertion (
    label VARCHAR(100) NOT NULL,
    ok INT NOT NULL,
    CONSTRAINT fixture_assertion_must_pass CHECK (ok = 1)
);
INSERT INTO fixture_assertion VALUES ('confirmed_database',
    IF(@fixture_expected_database <> '' AND DATABASE() = @fixture_expected_database, 1, 0));
INSERT INTO fixture_assertion VALUES ('confirmed_hostname',
    IF(@fixture_expected_hostname <> '' AND @@hostname = @fixture_expected_hostname, 1, 0));
INSERT INTO fixture_assertion
SELECT 'transactional_tables', IF(COUNT(*) = 3, 1, 0)
FROM information_schema.tables
WHERE table_schema = DATABASE() AND engine = 'InnoDB'
  AND table_name IN ('members', 'listings', 'listing_images');
INSERT INTO fixture_assertion VALUES ('foreign_keys_enabled', @@foreign_key_checks);

SET SESSION group_concat_max_len = 16777216;

-- 실행 전 확인
SELECT COUNT(*) AS listings_without_dong_before FROM listings WHERE dong_code IS NULL;
SELECT COUNT(*) AS members_with_dong_before FROM members WHERE dong_code IS NOT NULL;
SELECT login_id, dong_code AS s3_seller_dong_before FROM members WHERE login_id = 's3-seller-001';

START TRANSACTION;
UPDATE members SET dong_code = '1111053000'
WHERE login_id = 's3-seller-001' AND address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY' AND dong_code IS NULL;
SELECT ROW_COUNT() AS updated_s3_seller;

-- 판매자 행정동을 먼저 넣었으므로 s3-seller-001의 기존 매물도 같이 채워진다
CREATE TEMPORARY TABLE backfill_target (listing_id BIGINT PRIMARY KEY);
INSERT INTO backfill_target
SELECT l.id FROM listings l JOIN members m ON m.id = l.seller_id
WHERE l.dong_code IS NULL AND m.dong_code IS NOT NULL;
SET @backfill_target_count = (SELECT COUNT(*) FROM backfill_target);
SELECT @backfill_target_count AS backfill_listings,
       GROUP_CONCAT(listing_id ORDER BY listing_id) AS backfilled_listing_ids
FROM backfill_target;

UPDATE listings l
JOIN backfill_target t ON t.listing_id = l.id
JOIN members m ON m.id = l.seller_id
SET l.dong_code = m.dong_code;
INSERT INTO fixture_assertion VALUES ('all_targets_updated', IF(ROW_COUNT() = @backfill_target_count, 1, 0));
INSERT INTO fixture_assertion
SELECT 'no_fillable_listing_left', IF(COUNT(*) = 0, 1, 0)
FROM listings l JOIN members m ON m.id = l.seller_id
WHERE l.dong_code IS NULL AND m.dong_code IS NOT NULL;

-- 실행 후 확인(커밋 전 값)
SELECT COUNT(*) AS listings_without_dong_after FROM listings WHERE dong_code IS NULL;
SELECT login_id, dong_code AS s3_seller_dong_after FROM members WHERE login_id = 's3-seller-001';

ROLLBACK;

-- 롤백 후에는 실행 전 값과 같고, COMMIT으로 바꾼 실제 저장 후에는 실행 후 값과 같아야 한다.
SELECT COUNT(*) AS listings_without_dong_after_run FROM listings WHERE dong_code IS NULL;
SELECT login_id, dong_code AS s3_seller_dong_after_run FROM members WHERE login_id = 's3-seller-001';
