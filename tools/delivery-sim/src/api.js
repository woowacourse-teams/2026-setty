export class HttpError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

async function call(path, { method = 'GET', token, body } = {}) {
    const headers = {};
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body) headers['Content-Type'] = 'application/json';
    const res = await fetch(path, { method, headers, body: body && JSON.stringify(body) });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new HttpError(res.status, data?.message ?? `HTTP ${res.status}`);
    return data;
}

export async function login(loginId, password) {
    const { token } = await call('/api/delivery/auth/login', { method: 'POST', body: { loginId, password } });
    return token;
}

export function sendLocation(token, { lat, lng }) {
    return call('/api/delivery/location', { method: 'PUT', token, body: { lat, lng } });
}

export async function fetchRoute(from, to) {
    const coords = `${from.lng},${from.lat};${to.lng},${to.lat}`;
    const data = await call(`/osrm/route/v1/driving/${coords}?overview=full&geometries=geojson`);
    if (data.code !== 'Ok' || !data.routes.length) throw new Error(data.message ?? '경로를 찾지 못했습니다');
    const route = data.routes[0];
    return {
        points: route.geometry.coordinates.map(([lng, lat]) => ({ lat, lng })),
        distance: route.distance
    };
}

export async function fetchInProgressShipments(token) {
    const page = await call('/api/delivery/shipments?group=IN_PROGRESS&size=5', { token });
    return page.items;
}

export function fetchBundleCandidates(token) {
    return call('/api/delivery/requests/bundle', { token });
}
