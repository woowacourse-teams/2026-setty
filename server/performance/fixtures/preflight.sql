-- SSM에서 dev DB에 접속한 mysql 프롬프트에 실행하는 읽기 전용 확인.
-- 비밀번호, 토큰, 기존 회원의 개인정보는 조회하지 않는다.
-- DB 이름만으로 dev/prod를 판별하지 말고 접속한 EC2와 앱의 DB 대상도 확인한다.
SELECT DATABASE() AS database_name, @@hostname AS mysql_hostname,
       @@port AS mysql_port, VERSION() AS mysql_version;

SELECT COUNT(*) AS total_listings,
       COALESCE(SUM(sale_status = 'AVAILABLE' AND deleted_at IS NULL), 0)
           AS available_listings
FROM listings;

SELECT COUNT(*) AS fixture_sellers
FROM members WHERE login_id = 's2-listing-001';

SELECT table_name, engine
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('members', 'listings', 'listing_images')
ORDER BY table_name;

-- 다음 단계의 기대값: available_listings=0, fixture_sellers=0, 모두 InnoDB.
-- 다른 매물이 있으면 지우지 않고 조건을 먼저 조정한다.
