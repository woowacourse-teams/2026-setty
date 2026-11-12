-- 묶음 배송 후보를 고르는 250m 격자 칸 번호(GridCell). 좌표 자체는 저장하지 않는다.
-- 칸은 주문 때 저장한 건물 관리번호의 주소 기준표 좌표로 계산한다. 관리번호나 좌표가 없던 주문, 도입 전 요청은 NULL이고 묶음 후보에서 빠진다.
ALTER TABLE delivery
    ADD COLUMN pickup_cell INT NULL,
    ADD COLUMN delivery_cell INT NULL,
    ALGORITHM=INSTANT;

-- 칸 캐시가 (출발 칸, 도착 칸) 한 쌍의 대기 콜만 다시 읽을 때 쓴다.
ALTER TABLE delivery
    ADD INDEX idx_delivery_cells (pickup_cell, delivery_cell, status),
    ALGORITHM=INPLACE,
    LOCK=NONE;
