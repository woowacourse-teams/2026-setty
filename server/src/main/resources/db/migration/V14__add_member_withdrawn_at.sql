-- 탈퇴 시각. 탈퇴 회원은 행을 남기고 개인정보 칸만 가리므로 이 칸으로 구분한다. 적용 후에는 수정하지 말고 새 버전을 추가한다.
SET @member_withdrawn_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'withdrawn_at'
);
SET @add_member_withdrawn_at = IF(
    @member_withdrawn_at_exists = 0,
    'ALTER TABLE members ADD COLUMN withdrawn_at TIMESTAMP(6) NULL',
    'SELECT 1'
);
PREPARE add_member_withdrawn_at_statement FROM @add_member_withdrawn_at;
EXECUTE add_member_withdrawn_at_statement;
DEALLOCATE PREPARE add_member_withdrawn_at_statement;
