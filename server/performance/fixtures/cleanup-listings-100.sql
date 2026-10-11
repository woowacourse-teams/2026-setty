-- 실험 종료 시에만 사용. seed와 같은 dev 접속을 확인하고 새 mysql 배치 연결에서 실행한다.
-- 실행 명령은 ../README.md 참고. mysql --init-command로 아래 확인값을 전달한다:
-- @fixture_expected_database, @fixture_expected_hostname (SELECT @@hostname으로 확인한 값).
-- --force / 대화형 SOURCE 금지. 오류 시 클라이언트 종료로 전체를 롤백한다.
-- 기본은 ROLLBACK. 대상과 건수를 확인한 후에만 마지막을 COMMIT으로 바꾼다.
-- seed의 전용 판매자·100개·이미지 키가 일치하고 주문 등이 없을 때만 삭제할 수 있다.
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
SET @fixture_seller_id = (
    SELECT id FROM members WHERE login_id = 's2-listing-001'
      AND address = 'SETTY_SPRINT2_LISTING_001_FIXTURE_ONLY'
      AND password = REPEAT('!', 60) AND token IS NULL AND role = 'MEMBER'
);
INSERT INTO fixture_assertion VALUES ('fixture_seller_matches',
    IF(@fixture_seller_id IS NOT NULL, 1, 0));
INSERT INTO fixture_assertion
SELECT 'exactly_100_untouched_fixture_listings',
       IF(COUNT(*) = 100 AND SUM(title LIKE '[S2-L001] Test desk %'
           AND sale_status = 'AVAILABLE' AND deleted_at IS NULL
           AND has_purchase_request = FALSE) = 100, 1, 0)
FROM listings WHERE seller_id = @fixture_seller_id;
INSERT INTO fixture_assertion
SELECT 'exactly_100_fixture_images', IF(COUNT(*) = 100 AND SUM(
    i.object_key = CONCAT('setty/images/listings/sprint2-l001-', RIGHT(l.title, 6), '-01.jpg')
    AND i.display_order = 1) = 100, 1, 0)
FROM listing_images i JOIN listings l ON l.id = i.listing_id
WHERE l.seller_id = @fixture_seller_id;
INSERT INTO fixture_assertion
SELECT 'no_orders', IF(COUNT(*) = 0, 1, 0) FROM orders o
WHERE o.buyer_id = @fixture_seller_id OR o.listing_id IN
    (SELECT id FROM listings WHERE seller_id = @fixture_seller_id);
INSERT INTO fixture_assertion
SELECT 'no_favorites', IF(COUNT(*) = 0, 1, 0) FROM favorites f
WHERE f.member_id = @fixture_seller_id OR f.listing_id IN
    (SELECT id FROM listings WHERE seller_id = @fixture_seller_id);
INSERT INTO fixture_assertion
SELECT 'no_settlements', IF(COUNT(*) = 0, 1, 0) FROM settlements s
WHERE (s.payee_type = 'SELLER' AND s.payee_id = @fixture_seller_id)
   OR s.listing_id IN (SELECT id FROM listings WHERE seller_id = @fixture_seller_id);

DELETE i FROM listing_images i JOIN listings l ON l.id = i.listing_id
WHERE l.seller_id = @fixture_seller_id;
SELECT ROW_COUNT() AS deleted_fixture_images;
DELETE FROM listings WHERE seller_id = @fixture_seller_id;
SELECT ROW_COUNT() AS deleted_fixture_listings;
DELETE FROM members WHERE id = @fixture_seller_id;
SELECT ROW_COUNT() AS deleted_fixture_sellers;
ROLLBACK;

-- 위 삭제 건수는 커밋 전 값이다. 롤백 후에는 100/1, 실제 삭제 후에는 0/0이어야 한다.
SELECT COUNT(*) AS fixture_listings_after_run
FROM listings WHERE seller_id = @fixture_seller_id;
SELECT COUNT(*) AS fixture_sellers_after_run
FROM members WHERE id = @fixture_seller_id;
