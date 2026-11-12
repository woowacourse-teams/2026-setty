// 경로 위를 거리 기준으로 움직인다. 위경도 차이를 250m 격자와 같은 km 환산으로 잰다.
const KX = 111.32 * Math.cos((37.55 * Math.PI) / 180);
const KY = 110.95;

function meters(a, b) {
    return Math.hypot((a.lng - b.lng) * KX, (a.lat - b.lat) * KY) * 1000;
}

export function createPath(points) {
    const acc = [0];
    for (let i = 1; i < points.length; i++) acc.push(acc[i - 1] + meters(points[i - 1], points[i]));
    const length = acc[acc.length - 1];

    function at(distance) {
        const d = Math.min(Math.max(distance, 0), length);
        let i = 1;
        while (i < acc.length - 1 && acc[i] < d) i++;
        const seg = acc[i] - acc[i - 1];
        const t = seg === 0 ? 0 : (d - acc[i - 1]) / seg;
        const a = points[i - 1];
        const b = points[i];
        return { lat: a.lat + (b.lat - a.lat) * t, lng: a.lng + (b.lng - a.lng) * t };
    }

    return { points, length, at };
}
