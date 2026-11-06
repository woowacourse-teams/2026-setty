// 묶음 배송 위치 계산. 서버 GridCell(250m 격자)과 같은 식이어야 칸이 바뀌는 순간이 서버와 맞는다.

const LATITUDE_ORIGIN = 37.55;
const LONGITUDE_STEP = 0.25 / (111.32 * Math.cos((LATITUDE_ORIGIN * Math.PI) / 180));
const LATITUDE_STEP = 0.25 / 110.95;
const ROWS = 1 << 15;
const COLUMNS = 1 << 16;

/** 위도·경도가 속한 250m 칸 번호. 격자 밖이면 null. */
export function gridCellOf(latitude: number, longitude: number): number | null {
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
    const column = Math.floor(longitude / LONGITUDE_STEP);
    const row = Math.floor(latitude / LATITUDE_STEP);
    if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) return null;
    return column * ROWS + row;
}

export type SimulatorLink = { token: string; simulator: boolean };

/**
 * 시연 시뮬레이터 QR이 여는 주소의 조각(`#token=…&sim=1`)을 읽는다.
 * 조각은 서버로 가지 않아 토큰이 서버 로그에 남지 않는다. 토큰이 없으면 null.
 */
export function parseSimulatorLink(hash: string): SimulatorLink | null {
    const params = new URLSearchParams(hash.replace(/^#/, ''));
    const token = params.get('token');
    if (!token) return null;
    return { token, simulator: params.get('sim') === '1' };
}
