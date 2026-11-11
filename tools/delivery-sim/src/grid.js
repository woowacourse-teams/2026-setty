// 서버 GridCell, ~/osrm/phase8/grid_acc.py와 같은 식이다. 원점은 경위도 0, 칸 크기는 위도 37.55도 기준 250m.
const STEP_KM = 0.25;
const LAT0 = 37.55;
const KX = 111.32 * Math.cos((LAT0 * Math.PI) / 180);
const KY = 110.95;
export const DX = STEP_KM / KX;
export const DY = STEP_KM / KY;

export function cellOf(lat, lng) {
    return { x: Math.floor(lng / DX), y: Math.floor(lat / DY) };
}

export function cellBounds({ x, y }) {
    return [
        [y * DY, x * DX],
        [(y + 1) * DY, (x + 1) * DX]
    ];
}

// 서버가 칸을 int 하나로 다루는 번호(열 × 2^15 + 행).
export function cellCode({ x, y }) {
    return x * (1 << 15) + y;
}

export function cellFromCode(code) {
    return { x: Math.floor(code / (1 << 15)), y: code % (1 << 15) };
}
