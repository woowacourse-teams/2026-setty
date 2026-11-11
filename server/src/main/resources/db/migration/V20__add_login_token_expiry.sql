-- 로그인 토큰 만료 시각. 로그인할 때 토큰과 함께 쓰고, 이 시각이 지났거나 비어 있으면 토큰을 받지 않는다.
-- 적용 후에는 수정하지 말고 새 버전을 추가한다.
ALTER TABLE members
    ADD COLUMN token_expires_at TIMESTAMP(6) NULL AFTER token;  -- 토큰이 없으면 NULL

ALTER TABLE delivery_member
    ADD COLUMN token_expires_at TIMESTAMP(6) NULL AFTER token;  -- 토큰이 없으면 NULL

-- 이미 로그인돼 있는 토큰은 적용 시점부터 30일 동안 받는다. 비워 두면 배포 직후 모두 로그아웃된다.
-- 서버(JPA)가 이 칸을 UTC 시각으로 읽고 쓰므로 NOW가 아니라 UTC_TIMESTAMP로 채운다. DB 세션 시간대가 UTC가 아니어도 같은 시점이 된다.
UPDATE members
SET token_expires_at = TIMESTAMPADD(DAY, 30, UTC_TIMESTAMP(6))
WHERE token IS NOT NULL;

UPDATE delivery_member
SET token_expires_at = TIMESTAMPADD(DAY, 30, UTC_TIMESTAMP(6))
WHERE token IS NOT NULL;
