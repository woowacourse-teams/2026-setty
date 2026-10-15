-- 기존 전용 매물 5,000개의 사진 수만 1<->5로 변경한다.
-- MySQL 8.0.16+ / InnoDB. dev 확인/승인 후 새 배치 연결에서 실행.
-- init-command: @fixture_expected_database, @fixture_expected_hostname,
--               @fixture_expected_available_listings(읽기 확인한 전체 건수),
--               @fixture_images_before(1/5), @fixture_images_after(5/1).
-- --batch --skip-reconnect --skip-force 필수. 오류 후 연결 종료로 롤백한다.
-- 기본 ROLLBACK. 검증 후에만 임시 실행본의 ROLLBACK을 COMMIT으로 변경.
-- 매물/대표 사진/S3 파일/영구 스키마 변경 없음. 전용 데이터 쓰기와 겹치지 않는다.
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
INSERT INTO fixture_assertion VALUES ('explicit_image_counts',
    IF(@fixture_images_before IN (1, 5) AND @fixture_images_after IN (1, 5)
       AND @fixture_images_before <> @fixture_images_after, 1, 0));
INSERT INTO fixture_assertion VALUES ('explicit_available_count',
    IF(@fixture_expected_available_listings >= 5000, 1, 0));
INSERT INTO fixture_assertion
SELECT 'transactional_tables', IF(COUNT(*) = 3, 1, 0)
FROM information_schema.tables
WHERE table_schema = DATABASE() AND engine = 'InnoDB'
  AND table_name IN ('members', 'listings', 'listing_images');
INSERT INTO fixture_assertion VALUES ('foreign_keys_enabled', @@foreign_key_checks);

CREATE TEMPORARY TABLE fixture_orders (n INT PRIMARY KEY);
INSERT INTO fixture_orders VALUES (1), (2), (3), (4), (5);
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

CREATE TEMPORARY TABLE fixture_targets (id BIGINT PRIMARY KEY, suffix CHAR(6) NOT NULL UNIQUE);
START TRANSACTION;
SET @fixture_seller_id = (
    SELECT id FROM members WHERE login_id = 's2-listing-001'
      AND address = 'SETTY_SPRINT2_LISTING_001_FIXTURE_ONLY'
      AND password = REPEAT('!', 60) AND token IS NULL AND role = 'MEMBER'
);
INSERT INTO fixture_assertion VALUES ('fixture_seller_matches', IF(@fixture_seller_id IS NOT NULL, 1, 0));
INSERT INTO fixture_assertion
SELECT 'confirmed_available_listings', IF(COUNT(*) = @fixture_expected_available_listings, 1, 0)
FROM listings WHERE sale_status = 'AVAILABLE' AND deleted_at IS NULL;
INSERT INTO fixture_assertion
SELECT 'exactly_5000_fixture_listings', IF(COUNT(*) = 5000, 1, 0)
FROM listings WHERE seller_id = @fixture_seller_id;

INSERT INTO fixture_targets
SELECT l.id, LPAD(seq.n, 6, '0')
FROM listings l JOIN fixture_sequence seq
  ON l.title = CONCAT('[S2-L001] Test desk ', LPAD(seq.n, 6, '0'))
WHERE l.seller_id = @fixture_seller_id
  AND l.description = 'Synthetic listing for the Sprint 2 listing-count experiment. Not for purchase.'
  AND l.price = 50000 AND l.delivery_fee = 10000
  AND l.category = 'DESK' AND l.condition_grade = 'A'
  AND l.width_cm = 80 AND l.depth_cm = 50 AND l.height_cm = 70
  AND l.sale_status = 'AVAILABLE' AND l.has_purchase_request = FALSE
  AND l.deleted_at IS NULL
  AND l.created_at = TIMESTAMPADD(SECOND, seq.n, '2026-10-01 00:00:00')
  AND l.updated_at = l.created_at;
INSERT INTO fixture_assertion
SELECT 'original_fixture_fields_match', IF(COUNT(*) = 5000, 1, 0) FROM fixture_targets;

-- 전체 건수와 매물별 사진 키/순서를 함께 검사한다.
INSERT INTO fixture_assertion
SELECT 'fixture_images_before_match',
    IF(COUNT(*) = 5000 * @fixture_images_before
       AND SUM(i.display_order BETWEEN 1 AND @fixture_images_before
           AND i.object_key = CONCAT('setty/images/listings/sprint2-l001-', t.suffix,
                                    '-', LPAD(i.display_order, 2, '0'), '.jpg'))
           = 5000 * @fixture_images_before, 1, 0)
FROM listing_images i JOIN fixture_targets t ON t.id = i.listing_id;
INSERT INTO fixture_assertion
SELECT 'each_listing_images_before_match', IF(COUNT(*) = 5000, 1, 0)
FROM (
    SELECT t.id FROM fixture_targets t LEFT JOIN listing_images i ON i.listing_id = t.id
    GROUP BY t.id HAVING COUNT(i.id) = @fixture_images_before
        AND COUNT(DISTINCT i.display_order) = @fixture_images_before
) valid;

INSERT INTO listing_images (listing_id, object_key, display_order)
SELECT t.id, CONCAT('setty/images/listings/sprint2-l001-', t.suffix,
                    '-', LPAD(o.n, 2, '0'), '.jpg'), o.n
FROM fixture_targets t CROSS JOIN fixture_orders o
WHERE @fixture_images_before = 1 AND @fixture_images_after = 5 AND o.n BETWEEN 2 AND 5;
SET @fixture_added_images = ROW_COUNT();
INSERT INTO fixture_assertion VALUES ('added_images_match',
    IF(@fixture_added_images = IF(@fixture_images_after = 5, 20000, 0), 1, 0));

DELETE i FROM listing_images i JOIN fixture_targets t ON t.id = i.listing_id
WHERE @fixture_images_before = 5 AND @fixture_images_after = 1
  AND i.display_order BETWEEN 2 AND 5
  AND i.object_key = CONCAT('setty/images/listings/sprint2-l001-', t.suffix,
                           '-', LPAD(i.display_order, 2, '0'), '.jpg');
SET @fixture_removed_images = ROW_COUNT();
INSERT INTO fixture_assertion VALUES ('removed_images_match',
    IF(@fixture_removed_images = IF(@fixture_images_after = 1, 20000, 0), 1, 0));

INSERT INTO fixture_assertion
SELECT 'fixture_images_after_match',
    IF(COUNT(*) = 5000 * @fixture_images_after
       AND SUM(i.display_order BETWEEN 1 AND @fixture_images_after
           AND i.object_key = CONCAT('setty/images/listings/sprint2-l001-', t.suffix,
                                    '-', LPAD(i.display_order, 2, '0'), '.jpg'))
           = 5000 * @fixture_images_after, 1, 0)
FROM listing_images i JOIN fixture_targets t ON t.id = i.listing_id;
INSERT INTO fixture_assertion
SELECT 'each_listing_images_after_match', IF(COUNT(*) = 5000, 1, 0)
FROM (
    SELECT t.id FROM fixture_targets t LEFT JOIN listing_images i ON i.listing_id = t.id
    GROUP BY t.id HAVING COUNT(i.id) = @fixture_images_after
        AND COUNT(DISTINCT i.display_order) = @fixture_images_after
) valid;

SELECT @fixture_added_images AS added_images, @fixture_removed_images AS removed_images;
SELECT COUNT(*) AS fixture_listings_before_finish FROM listings WHERE seller_id = @fixture_seller_id;
SELECT COUNT(*) AS fixture_images_before_finish
FROM listing_images i JOIN listings l ON l.id = i.listing_id WHERE l.seller_id = @fixture_seller_id;
INSERT INTO fixture_assertion
SELECT 'available_listings_unchanged', IF(COUNT(*) = @fixture_expected_available_listings, 1, 0)
FROM listings WHERE sale_status = 'AVAILABLE' AND deleted_at IS NULL;
ROLLBACK;

-- ROLLBACKなら元の写真数、COMMITなら変更後の写真数。最後の出力で保存を確認する。
SELECT COUNT(*) AS fixture_listings_after_run FROM listings WHERE seller_id = @fixture_seller_id;
SELECT COUNT(*) AS fixture_images_after_run
FROM listing_images i JOIN listings l ON l.id = i.listing_id WHERE l.seller_id = @fixture_seller_id;
SELECT COUNT(*) AS available_listings_after_run
FROM listings WHERE sale_status = 'AVAILABLE' AND deleted_at IS NULL;
