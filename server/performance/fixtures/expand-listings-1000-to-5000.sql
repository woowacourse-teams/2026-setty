-- 기존 Sprint 2 매물 1,000개를 유지하고 같은 판매자에게 4,000개를 추가한다.
-- MySQL 8.0.16+ / InnoDB. dev 대상 확인 후 새 mysql 배치 연결에서 실행한다.
-- --init-command로 @fixture_expected_database, @fixture_expected_hostname 전달.
-- --skip-reconnect --skip-force 필수. 대화형 SOURCE 금지.
-- 기본 ROLLBACK: 시험 통과 후 임시 실행본의 마지막 ROLLBACK만 COMMIT으로 변경.
-- S3 업로드/영구 스키마 변경 없음. 다른 테스트·데이터 변경과 겹치지 않게 실행한다.
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

CREATE TEMPORARY TABLE fixture_sequence (n INT PRIMARY KEY);
INSERT INTO fixture_sequence
SELECT thousands.n * 1000 + hundreds.n * 100 + tens.n * 10 + ones.n + 1
FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
      UNION ALL SELECT 4) thousands
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
      UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
      UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
      UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
      UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
      UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
      UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones;

START TRANSACTION;
SET @fixture_seller_id = (
    SELECT id FROM members WHERE login_id = 's2-listing-001'
      AND address = 'SETTY_SPRINT2_LISTING_001_FIXTURE_ONLY'
      AND password = REPEAT('!', 60) AND token IS NULL AND role = 'MEMBER'
);
INSERT INTO fixture_assertion VALUES ('fixture_seller_matches',
    IF(@fixture_seller_id IS NOT NULL, 1, 0));
INSERT INTO fixture_assertion
SELECT 'exactly_1000_available_listings_before', IF(COUNT(*) = 1000, 1, 0)
FROM listings WHERE sale_status = 'AVAILABLE' AND deleted_at IS NULL;
INSERT INTO fixture_assertion
SELECT 'exactly_1000_fixture_listings_before', IF(COUNT(*) = 1000, 1, 0)
FROM listings WHERE seller_id = @fixture_seller_id;
INSERT INTO fixture_assertion
SELECT 'original_fixture_fields_match',
       IF(COUNT(*) = 1000 AND COUNT(DISTINCT seq.n) = 1000, 1, 0)
FROM listings l JOIN fixture_sequence seq
  ON l.title = CONCAT('[S2-L001] Test desk ', LPAD(seq.n, 6, '0'))
WHERE l.seller_id = @fixture_seller_id AND seq.n <= 1000
  AND l.description = 'Synthetic listing for the Sprint 2 listing-count experiment. Not for purchase.'
  AND l.price = 50000 AND l.delivery_fee = 10000
  AND l.category = 'DESK' AND l.condition_grade = 'A'
  AND l.width_cm = 80 AND l.depth_cm = 50 AND l.height_cm = 70
  AND l.sale_status = 'AVAILABLE' AND l.has_purchase_request = FALSE
  AND l.deleted_at IS NULL
  AND l.created_at = TIMESTAMPADD(SECOND, seq.n, '2026-10-01 00:00:00')
  AND l.updated_at = l.created_at;
INSERT INTO fixture_assertion
SELECT 'original_fixture_images_match',
       IF(COUNT(*) = 1000 AND COUNT(DISTINCT i.listing_id) = 1000
          AND SUM(i.display_order = 1 AND i.object_key = CONCAT(
              'setty/images/listings/sprint2-l001-', RIGHT(l.title, 6), '-01.jpg')) = 1000, 1, 0)
FROM listing_images i JOIN listings l ON l.id = i.listing_id
WHERE l.seller_id = @fixture_seller_id;

INSERT INTO listings
    (seller_id, title, description, price, delivery_fee, category, condition_grade,
     width_cm, depth_cm, height_cm, sale_status, has_purchase_request,
     created_at, updated_at, deleted_at)
SELECT @fixture_seller_id,
       CONCAT('[S2-L001] Test desk ', LPAD(seq.n, 6, '0')),
       'Synthetic listing for the Sprint 2 listing-count experiment. Not for purchase.',
       50000, 10000, 'DESK', 'A', 80, 50, 70, 'AVAILABLE', FALSE,
       TIMESTAMPADD(SECOND, seq.n, '2026-10-01 00:00:00'),
       TIMESTAMPADD(SECOND, seq.n, '2026-10-01 00:00:00'), NULL
FROM fixture_sequence seq WHERE seq.n BETWEEN 1001 AND 5000
ORDER BY seq.n;
SET @fixture_added_listings = ROW_COUNT();
INSERT INTO fixture_assertion VALUES ('added_4000_listings', IF(@fixture_added_listings = 4000, 1, 0));

INSERT INTO listing_images (listing_id, object_key, display_order)
SELECT l.id, CONCAT('setty/images/listings/sprint2-l001-', RIGHT(l.title, 6), '-01.jpg'), 1
FROM listings l JOIN fixture_sequence seq
  ON l.title = CONCAT('[S2-L001] Test desk ', LPAD(seq.n, 6, '0'))
WHERE l.seller_id = @fixture_seller_id AND seq.n BETWEEN 1001 AND 5000;
SET @fixture_added_images = ROW_COUNT();
INSERT INTO fixture_assertion VALUES ('added_4000_images', IF(@fixture_added_images = 4000, 1, 0));
INSERT INTO fixture_assertion
SELECT 'exactly_5000_fixture_listings', IF(COUNT(*) = 5000 AND COUNT(DISTINCT title) = 5000, 1, 0)
FROM listings WHERE seller_id = @fixture_seller_id;
INSERT INTO fixture_assertion
SELECT 'exactly_5000_available_listings', IF(COUNT(*) = 5000, 1, 0)
FROM listings WHERE sale_status = 'AVAILABLE' AND deleted_at IS NULL;
INSERT INTO fixture_assertion
SELECT 'exactly_one_matching_image_per_listing',
       IF(COUNT(*) = 5000 AND COUNT(DISTINCT i.listing_id) = 5000
          AND SUM(i.display_order = 1 AND i.object_key = CONCAT(
              'setty/images/listings/sprint2-l001-', RIGHT(l.title, 6), '-01.jpg')) = 5000, 1, 0)
FROM listing_images i JOIN listings l ON l.id = i.listing_id
WHERE l.seller_id = @fixture_seller_id;

SELECT @fixture_added_listings AS added_listings, @fixture_added_images AS added_images;
SELECT @fixture_seller_id AS fixture_seller_id, COUNT(*) AS fixture_listings_before_finish
FROM listings WHERE seller_id = @fixture_seller_id;
SELECT COUNT(*) AS fixture_images_before_finish
FROM listing_images i JOIN listings l ON l.id = i.listing_id
WHERE l.seller_id = @fixture_seller_id;
ROLLBACK;

-- 롤백 후에는 1000/1000/1000, COMMIT 후에는 5000/5000/5000이어야 한다.
SELECT COUNT(*) AS fixture_listings_after_run FROM listings WHERE seller_id = @fixture_seller_id;
SELECT COUNT(*) AS fixture_images_after_run
FROM listing_images i JOIN listings l ON l.id = i.listing_id
WHERE l.seller_id = @fixture_seller_id;
SELECT COUNT(*) AS available_listings_after_run
FROM listings WHERE sale_status = 'AVAILABLE' AND deleted_at IS NULL;
