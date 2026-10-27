-- C1 종료와 롤백 기간 종료 후 적용한다. C2는 기존 컬럼의 NULL을 처리할 수 있어야 한다.
ALTER TABLE listings MODIFY COLUMN title VARCHAR(100) NULL;
