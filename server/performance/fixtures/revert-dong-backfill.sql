-- backfill-dong-codes.sql을 되돌린다. 채운 매물의 dong_code를 다시 NULL로, s3-seller-001의 행정동을 다시 NULL로 한다.
-- --init-command로 아래 값을 함께 전달한다(채우기 실제 실행 출력에서 복사):
--   @fixture_expected_database, @fixture_expected_hostname,
--   @backfilled_listing_ids ('1,2,3' 형식, 없으면 ''), @revert_s3_seller (updated_s3_seller 값, 0 또는 1)
-- --skip-reconnect --skip-force 필수. 기본은 마지막 ROLLBACK, 시험 통과 후 임시 실행본만 COMMIT.
-- 매물 dong_code는 등록 뒤 바뀌지 않으므로(updatable=false) 목록의 id 값은 모두 채우기가 넣은 값이다.

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

INSERT INTO fixture_assertion VALUES ('backfill_output_given',
    IF(@backfilled_listing_ids IS NOT NULL AND @revert_s3_seller IN (0, 1), 1, 0));

START TRANSACTION;
CREATE TEMPORARY TABLE revert_target (listing_id BIGINT PRIMARY KEY);
INSERT INTO revert_target
SELECT id FROM listings WHERE FIND_IN_SET(id, @backfilled_listing_ids) > 0;
SELECT COUNT(*) AS revert_listings FROM revert_target;

UPDATE listings l JOIN revert_target t ON t.listing_id = l.id SET l.dong_code = NULL;
SELECT ROW_COUNT() AS reverted_listings;
UPDATE members SET dong_code = NULL
WHERE @revert_s3_seller = 1 AND login_id = 's3-seller-001'
  AND address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY' AND dong_code = '1111053000';
SELECT ROW_COUNT() AS reverted_s3_seller;
ROLLBACK;

SELECT COUNT(*) AS listings_without_dong_after_run FROM listings WHERE dong_code IS NULL;
SELECT login_id, dong_code AS s3_seller_dong_after_run FROM members WHERE login_id = 's3-seller-001';
