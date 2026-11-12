-- 기사별 완료 내역이 쌓여도 수락 시각과 ID 경계에서 다음 페이지를 정렬 없이 읽는다.
-- 진행 중처럼 상태별 범위가 더 작은 조회는 기존 idx_delivery_requests도 선택할 수 있다.
ALTER TABLE delivery
    ADD INDEX idx_delivery_shipments (driver_id, accepted_at, id),
    ALGORITHM=INPLACE,
    LOCK=NONE;
