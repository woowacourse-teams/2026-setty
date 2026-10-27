-- 로컬 스키마 실험 전용. C0가 실행 중이므로 기존 컬럼과 제약은 유지한다.
ALTER TABLE listings ADD COLUMN display_title VARCHAR(100) NULL;
