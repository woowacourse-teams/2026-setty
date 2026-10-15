-- Flyway 최초 편입용: 빈 DB와 기존 DB 모두 지원한다. 적용 후에는 수정하지 말고 새 버전을 추가한다.

-- Spring Modulith 이벤트 발행 기록(spring-modulith-events-jdbc v2 MySQL 스키마).
-- 모듈 간 이벤트를 리스너별로 기록하고, 처리에 실패하거나 남은 건을 재발행한다.
-- Modulith가 대문자 테이블명으로 조회하므로 이름을 바꾸지 않는다.
CREATE TABLE IF NOT EXISTS EVENT_PUBLICATION (
    ID                     VARCHAR(36)   NOT NULL,
    LISTENER_ID            VARCHAR(512)  NOT NULL,
    EVENT_TYPE             VARCHAR(512)  NOT NULL,
    SERIALIZED_EVENT       VARCHAR(4000) NOT NULL,
    PUBLICATION_DATE       TIMESTAMP(6)  NOT NULL,
    COMPLETION_DATE        TIMESTAMP(6)  DEFAULT NULL NULL,
    STATUS                 VARCHAR(20),
    COMPLETION_ATTEMPTS    INT,
    LAST_RESUBMISSION_DATE TIMESTAMP(6)  DEFAULT NULL NULL,
    PRIMARY KEY (ID),
    INDEX EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX (COMPLETION_DATE)
);
