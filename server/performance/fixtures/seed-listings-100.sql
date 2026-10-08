-- Sprint 2 첫 탐색: 전용 판매자 1명 + AVAILABLE 매물 100개 + 사진 메타데이터 100개.
-- MySQL 8.0.16+ 전용. preflight.sql 결과와 dev 접속 대상을 먼저 확인한다.
-- 실행 명령은 ../README.md 참고. mysql --init-command로 아래 확인값을 전달한다:
-- @fixture_expected_database, @fixture_expected_hostname (SELECT @@hostname으로 확인한 값).
-- 반드시 새 연결의 배치 모드로 실행한다. --force 또는 대화형 SOURCE 금지:
-- 오류 시 클라이언트가 종료되어 미커밋 변경 전체가 롤백되는 방식이다.
-- 확인값이 없거나 대상과 다르면 INSERT 전에 중단한다. 비밀번호는 저장하지 않는다.
-- 기본은 마지막 ROLLBACK으로 시험한다. 검증 후 실제 적용할 때만 COMMIT으로 바꾼다.
-- 영구 스키마 변경이나 S3 업로드 없음. 재실행 시 기존 전용 계정을 발견하면 중단한다.

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

START TRANSACTION;
INSERT INTO fixture_assertion
SELECT 'no_existing_available_listings', IF(COUNT(*) = 0, 1, 0)
FROM listings WHERE sale_status = 'AVAILABLE' AND deleted_at IS NULL;
INSERT INTO fixture_assertion
SELECT 'fixture_seller_does_not_exist', IF(COUNT(*) = 0, 1, 0)
FROM members WHERE login_id = 's2-listing-001';

-- 이 계정은 로그인하지 않는다. password는 BCrypt 해시가 아닌 로그인 불가 표식이다.
INSERT INTO members (login_id, password, role, phone_number, address, token)
VALUES ('s2-listing-001', REPEAT('!', 60), 'MEMBER', '000-0000-0000',
        'SETTY_SPRINT2_LISTING_001_FIXTURE_ONLY', NULL);
SET @fixture_seller_id = LAST_INSERT_ID();

-- 0~9 두 자리를 조합해 1~100을 만든다. 모든 문자열 길이와 사진 수는 동일하다.
-- 80*50*70=280000 cm3이므로 현재 배송비 정책의 10000원에 해당한다.
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
FROM (
    SELECT tens.n * 10 + ones.n + 1 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
          UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
          UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
          UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
          UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
) seq
ORDER BY seq.n;

-- 앱의 publicUrl이 요구하는 경로 접두사를 사용한다.
-- 실체 없는 키이므로 이미지 표시 테스트에는 쓰지 않는다. 목록 API는 URL 문자열만 반환한다.
INSERT INTO listing_images (listing_id, object_key, display_order)
SELECT id, CONCAT('setty/images/listings/sprint2-l001-', RIGHT(title, 6), '-01.jpg'), 1
FROM listings WHERE seller_id = @fixture_seller_id;

INSERT INTO fixture_assertion
SELECT 'exactly_100_fixture_listings', IF(COUNT(*) = 100, 1, 0)
FROM listings WHERE seller_id = @fixture_seller_id;
INSERT INTO fixture_assertion
SELECT 'exactly_one_image_per_listing', IF(COUNT(*) = 100, 1, 0)
FROM (
    SELECT l.id FROM listings l LEFT JOIN listing_images i ON i.listing_id = l.id
    WHERE l.seller_id = @fixture_seller_id
    GROUP BY l.id HAVING COUNT(i.id) = 1 AND MIN(i.display_order) = 1
) valid;
INSERT INTO fixture_assertion
SELECT 'exactly_100_available_listings', IF(COUNT(*) = 100, 1, 0)
FROM listings WHERE sale_status = 'AVAILABLE' AND deleted_at IS NULL;

SELECT @fixture_seller_id AS fixture_seller_id, COUNT(*) AS fixture_listings
FROM listings WHERE seller_id = @fixture_seller_id;
SELECT COUNT(*) AS fixture_images FROM listing_images i
JOIN listings l ON l.id = i.listing_id WHERE l.seller_id = @fixture_seller_id;

-- 기본은 롤백. 위의 100/100 출력은 커밋 전 검증값이며 저장 완료를 뜻하지 않는다.
ROLLBACK;

-- 롤백 후에는 0/0, COMMIT으로 바꾼 실제 저장 후에는 100/1이어야 한다.
SELECT COUNT(*) AS available_listings_after_run
FROM listings WHERE sale_status = 'AVAILABLE' AND deleted_at IS NULL;
SELECT COUNT(*) AS fixture_sellers_after_run
FROM members WHERE login_id = 's2-listing-001';
