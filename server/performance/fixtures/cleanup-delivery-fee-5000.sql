-- seed-delivery-fee-5000.sql로 넣은 판매자 427명·매물 5,000개·사진 메타데이터 5,000개를 지운다.
-- MySQL 8.0.16+ / InnoDB. --init-command로 @fixture_expected_database, @fixture_expected_hostname 전달.
-- --skip-reconnect --skip-force 필수. 기본은 마지막 ROLLBACK, 시험 통과 후 임시 실행본만 COMMIT.
-- 표식·건수·상태가 시드와 다르거나 주문·찜이 있으면 지우지 않고 중단한다.

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
CREATE TEMPORARY TABLE fixture_listing (id BIGINT PRIMARY KEY);
INSERT INTO fixture_listing
SELECT l.id FROM listings l JOIN members m ON m.id = l.seller_id
WHERE m.address = 'SETTY_DELIVERY_FEE_FILTER_FIXTURE_ONLY';

INSERT INTO fixture_assertion
SELECT 'exactly_427_fixture_sellers',
       IF(COUNT(*) = 427 AND SUM(login_id LIKE 's4-fee-%' AND password = REPEAT('!', 60)
           AND token IS NULL AND role = 'MEMBER') = 427, 1, 0)
FROM members WHERE address = 'SETTY_DELIVERY_FEE_FILTER_FIXTURE_ONLY';
INSERT INTO fixture_assertion
SELECT 'exactly_5000_untouched_fixture_listings',
       IF(COUNT(*) = 5000 AND SUM(l.title LIKE '[S4-F001] Test furniture %'
           AND l.sale_status = 'AVAILABLE' AND l.deleted_at IS NULL
           AND l.has_purchase_request = FALSE) = 5000, 1, 0)
FROM listings l JOIN fixture_listing f ON f.id = l.id;
INSERT INTO fixture_assertion
SELECT 'no_orders_on_fixture_listings', IF(COUNT(*) = 0, 1, 0)
FROM orders o JOIN fixture_listing f ON f.id = o.listing_id;
INSERT INTO fixture_assertion
SELECT 'no_favorites_on_fixture_listings', IF(COUNT(*) = 0, 1, 0)
FROM favorites v JOIN fixture_listing f ON f.id = v.listing_id;

DELETE i FROM listing_images i JOIN fixture_listing f ON f.id = i.listing_id;
SELECT ROW_COUNT() AS deleted_fixture_images;
DELETE l FROM listings l JOIN fixture_listing f ON f.id = l.id;
SELECT ROW_COUNT() AS deleted_fixture_listings;
DELETE FROM members WHERE address = 'SETTY_DELIVERY_FEE_FILTER_FIXTURE_ONLY';
SELECT ROW_COUNT() AS deleted_fixture_sellers;
ROLLBACK;

-- 위 삭제 건수는 커밋 전 값이다. 롤백 후에는 427/5000, 실제 삭제 후에는 0/0이어야 한다.
SELECT COUNT(*) AS fixture_sellers_after_run
FROM members WHERE address = 'SETTY_DELIVERY_FEE_FILTER_FIXTURE_ONLY';
SELECT COUNT(*) AS fixture_listings_after_run
FROM listings l JOIN members m ON m.id = l.seller_id
WHERE m.address = 'SETTY_DELIVERY_FEE_FILTER_FIXTURE_ONLY';
