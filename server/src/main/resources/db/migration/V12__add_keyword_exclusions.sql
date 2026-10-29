-- 키워드 하나에 거는 제외 키워드 여러 개(최대 10개는 서버가 검사). 적용 후에는 수정하지 말고 새 버전을 추가한다.
-- 매칭은 "제목에 제외어가 하나라도 들어가면 안 보냄"이라 INSERT … SELECT 안에서 NOT EXISTS로 거른다.
-- 기존 keyword_subscriptions.exclude_keyword 컬럼은 더 쓰지 않고 비워 둔다.
CREATE TABLE IF NOT EXISTS keyword_exclusions (
    subscription_id BIGINT      NOT NULL,
    word            VARCHAR(20) NOT NULL,          -- 양쪽 공백 제거 후 1~20자. 매칭은 콜레이션으로 대소문자 무시
    PRIMARY KEY (subscription_id, word),
    CONSTRAINT fk_keyword_exclusions_subscription FOREIGN KEY (subscription_id)
        REFERENCES keyword_subscriptions (id) ON DELETE CASCADE
);
