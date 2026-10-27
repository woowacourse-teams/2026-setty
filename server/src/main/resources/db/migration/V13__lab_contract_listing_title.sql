-- C0~C2 종료·백필 검증·롤백 기간 종료 후 적용한다. NULL이 남으면 이 변경을 진행하지 않는다.
ALTER TABLE listings
    MODIFY COLUMN display_title VARCHAR(100) NOT NULL,
    DROP COLUMN title;
