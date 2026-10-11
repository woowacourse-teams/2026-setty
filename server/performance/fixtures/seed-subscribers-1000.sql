-- Sprint 3 알림 발송 측정: 등록용 판매자 1명 + 구독자 1,000명 + 같은 키워드 구독 1,000건.
-- MySQL 8.0.16+ 전용. preflight.sql 결과와 dev 접속 대상을 먼저 확인한다.
-- 실행 명령은 ../README.md 참고. mysql --init-command로 아래 확인값을 전달한다:
-- @fixture_expected_database, @fixture_expected_hostname, @fixture_seller_token(36자 UUID, Gatling SELLER_TOKEN과 같은 값).
-- 반드시 새 연결의 배치 모드로 실행한다. --force 또는 대화형 SOURCE 금지.
-- 기본은 마지막 ROLLBACK으로 시험한다. 검증 후 실제 적용할 때만 COMMIT으로 바꾼다.
-- 구독자 계정은 로그인하지 않는다. 판매자 토큰은 저장소에 두지 않고 실행 시 전달한다.

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
INSERT INTO fixture_assertion VALUES ('seller_token_provided',
    IF(CHAR_LENGTH(@fixture_seller_token) = 36, 1, 0));
INSERT INTO fixture_assertion
SELECT 'transactional_tables', IF(COUNT(*) = 4, 1, 0)
FROM information_schema.tables
WHERE table_schema = DATABASE() AND engine = 'InnoDB'
  AND table_name IN ('members', 'listings', 'keyword_subscriptions', 'listing_notifications');
INSERT INTO fixture_assertion VALUES ('foreign_keys_enabled', @@foreign_key_checks);

START TRANSACTION;
INSERT INTO fixture_assertion
SELECT 'fixture_members_do_not_exist', IF(COUNT(*) = 0, 1, 0)
FROM members WHERE login_id = 's3-seller-001' OR login_id LIKE 's3-sub-%';
INSERT INTO fixture_assertion
SELECT 'seller_token_unused', IF(COUNT(*) = 0, 1, 0)
FROM members WHERE token = @fixture_seller_token;

INSERT INTO members (login_id, password, role, phone_number, address, token)
VALUES ('s3-seller-001', REPEAT('!', 60), 'MEMBER', '000-0000-0000',
        'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY', @fixture_seller_token);

INSERT INTO members (login_id, password, role, phone_number, address, token)
SELECT CONCAT('s3-sub-', LPAD(seq.n, 6, '0')), REPEAT('!', 60), 'MEMBER', '000-0000-0000',
       'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY', NULL
FROM (
    SELECT h.n * 100 + t.n * 10 + o.n + 1 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) h
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) o
) seq
ORDER BY seq.n;

INSERT INTO keyword_subscriptions (member_id, keyword, created_at)
SELECT id, 's3-notify', NOW(6)
FROM members WHERE login_id LIKE 's3-sub-%' AND address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY';

SELECT COUNT(*) AS fixture_subscribers_after_run
FROM members WHERE login_id LIKE 's3-sub-%';
SELECT COUNT(*) AS fixture_subscriptions_after_run
FROM keyword_subscriptions WHERE keyword = 's3-notify';
SELECT COUNT(*) AS fixture_sellers_after_run
FROM members WHERE login_id = 's3-seller-001';

ROLLBACK;
