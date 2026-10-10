-- 구독자 1000명 → 10000명 확장. seed-subscribers-1000.sql이 저장된 뒤 같은 규칙으로 실행한다.
-- --init-command로 @fixture_expected_database, @fixture_expected_hostname을 전달한다.
-- 기본은 마지막 ROLLBACK으로 시험한다. 검증 후 실제 적용할 때만 COMMIT으로 바꾼다.

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
INSERT INTO fixture_assertion VALUES ('foreign_keys_enabled', @@foreign_key_checks);

START TRANSACTION;
INSERT INTO fixture_assertion
SELECT 'exactly_1000_fixture_subscribers', IF(COUNT(*) = 1000, 1, 0)
FROM members WHERE login_id LIKE 's3-sub-%' AND address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY';

INSERT INTO members (login_id, password, role, phone_number, address, token)
SELECT CONCAT('s3-sub-', LPAD(seq.n, 6, '0')), REPEAT('!', 60), 'MEMBER', '000-0000-0000',
       'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY', NULL
FROM (
    SELECT a.n * 1000 + b.n * 100 + c.n * 10 + d.n * 1 + 1 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
) seq
WHERE seq.n > 1000 AND seq.n <= 10000
ORDER BY seq.n;

INSERT INTO keyword_subscriptions (member_id, keyword, created_at)
SELECT m.id, 's3-notify', NOW(6)
FROM members m
LEFT JOIN keyword_subscriptions s ON s.member_id = m.id AND s.keyword = 's3-notify'
WHERE m.login_id LIKE 's3-sub-%' AND m.address = 'SETTY_SPRINT3_NOTIFY_FIXTURE_ONLY' AND s.id IS NULL;

SELECT COUNT(*) AS fixture_subscribers_after_run
FROM members WHERE login_id LIKE 's3-sub-%';
SELECT COUNT(*) AS fixture_subscriptions_after_run
FROM keyword_subscriptions WHERE keyword = 's3-notify';

ROLLBACK;
