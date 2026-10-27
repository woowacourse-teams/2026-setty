-- 미배정 요청은 취소 이력(driver_id IS NULL)과 구분하고 등록 최신순으로 읽는다.
-- 기사별 내역도 (status IN (...), driver_id) 범위를 이 인덱스로 선별할 수 있다.
ALTER TABLE delivery
    ADD INDEX idx_delivery_requests (status, driver_id, requested_at, id),
    ALGORITHM=INPLACE,
    LOCK=NONE;
