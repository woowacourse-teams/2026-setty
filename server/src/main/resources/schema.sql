-- SETTY 스키마. ddl-auto는 validate 고정이므로 스키마 변경은 이 파일에 SQL 추가로만 한다.
-- 매 부팅 시 실행되므로 모든 문장은 멱등이어야 한다 (CREATE TABLE IF NOT EXISTS, 컬럼 추가는 새 문장 대신 팀 합의 후 정리).

-- 빈 스크립트는 부팅이 실패하므로, 첫 테이블이 생기면 아래 문장은 지운다.
SELECT 1;
