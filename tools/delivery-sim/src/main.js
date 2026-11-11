import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import QRCode from 'qrcode';
import { HttpError, fetchBundleCandidates, fetchInProgressShipments, fetchRoute, login, sendLocation } from './api.js';
import { cellBounds, cellCode, cellFromCode, cellOf } from './grid.js';
import { createPath } from './route.js';

const config = __SIM_CONFIG__;
const BASE_SPEED_MPS = 30_000 / 3600;
const SEND_INTERVAL_MS = 1500;
const TOKEN_KEY = 'delivery-sim:token';
const LOGIN_ID_KEY = 'delivery-sim:login-id';
// 세티 배송원 웹 색을 따른다. 바탕은 무채색, 격자는 회색, 차의 경로와 지금 칸 테두리는 먹색, 차·후보만 파란 강조색
const GRID = '#8B95A1';
const INK = '#191F28';
const ROUTE = '#4E5968';
const OTHER_CALL = '#6B7684';
const BRAND = '#3182F6';

const $ = (id) => document.getElementById(id);

const state = {
    token: null,
    loginId: null,
    car: null,
    path: null,
    travelled: 0,
    running: false,
    sending: false,
    resend: false,
    currentCellKey: null,
    serverCell: null,
    destCell: null,
    bundle: null
};

// ---------- 지도와 격자 ----------
const [south, west, cols, rows] = config.area;
const origin = cellOf(south, west);
const areaCells = [];
for (let dy = 0; dy < rows; dy++) {
    for (let dx = 0; dx < cols; dx++) areaCells.push({ x: origin.x + dx, y: origin.y + dy });
}

const gridBounds = L.latLngBounds(areaCells.flatMap(cellBounds));
// 시연은 격자 안에서만 하므로 지도를 격자 주변에 묶어 두고 더 멀리 줄이지 못하게 한다
// 줌을 0.25 단계로 두어 격자가 지도 칸을 거의 꽉 채우게 한다
const map = L.map('map', { maxBounds: gridBounds.pad(0.35), maxBoundsViscosity: 1, zoomSnap: 0.25, zoomDelta: 0.5 });
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    className: 'base-tiles',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);
// 창 크기가 바뀌면 격자가 다시 꽉 차게 맞추고, 그보다 멀리 줄이지 못하게 한다
function fitGrid() {
    map.setMinZoom(0);
    map.fitBounds(gridBounds.pad(0.08));
    map.setMinZoom(map.getZoom());
}
fitGrid();
map.on('resize', fitGrid);

// 격자 밖을 흐리게 덮는다(세계 전체 사각형에 격자 모양 구멍)
const [[gs, gw], [gn, ge]] = [[gridBounds.getSouth(), gridBounds.getWest()], [gridBounds.getNorth(), gridBounds.getEast()]];
L.polygon([
    [[-85, -180], [-85, 180], [85, 180], [85, -180]],
    [[gs, gw], [gn, gw], [gn, ge], [gs, ge]]
], { stroke: false, fillColor: '#FFFFFF', fillOpacity: 0.7, interactive: false }).addTo(map);

const cellLayers = new Map();
for (const cell of areaCells) {
    const rect = L.rectangle(cellBounds(cell), { color: GRID, weight: 1, fillOpacity: 0 }).addTo(map);
    rect.bindTooltip(`칸 ${cellCode(cell)} (${cell.x}, ${cell.y})`, { sticky: true });
    cellLayers.set(key(cell), rect);
}

const currentCellLayer = L.rectangle([[0, 0], [0, 0]], { color: INK, weight: 2.5, fill: false, interactive: false });
const routeLine = L.polyline([], { color: ROUTE, weight: 5, opacity: 0.55 }).addTo(map);
// 후보 칸 글씨가 차를 가리지 않게 차와 목적지는 맨 위 층에 그린다
map.createPane('car').style.zIndex = 650;
const carMarker = L.circleMarker([0, 0], { pane: 'car', radius: 9, color: '#fff', weight: 3, fillColor: BRAND, fillOpacity: 1 });
const destMarker = L.circleMarker([0, 0], { pane: 'car', radius: 7, color: ROUTE, weight: 3, fillColor: '#fff', fillOpacity: 1 });

function key(cell) {
    return `${cell.x},${cell.y}`;
}

// ---------- 로그인과 QR ----------
// 서버 토큰은 만료가 없어 시연이 끝난 뒤 노트북에 남지 않게 탭을 닫으면 사라지는 sessionStorage에 둔다.
function readStored(name) {
    try {
        return sessionStorage.getItem(name);
    } catch {
        return null;
    }
}

function writeStored(name, value) {
    try {
        if (value == null) sessionStorage.removeItem(name);
        else sessionStorage.setItem(name, value);
    } catch {
        // 저장이 막혀도 이번 화면에서는 메모리의 토큰으로 계속 쓴다
    }
}

async function showLoggedIn() {
    const loggedIn = Boolean(state.token);
    state.serverCell = null;
    clearCandidates();
    $('server-cell').textContent = '-';
    $('bundle').textContent = '-';
    showBundle(null);
    setBadge('badge-login', loggedIn ? 'on' : null, loggedIn ? `${state.loginId} 연결됨` : '로그인 전');
    setBadge('badge-send', null, '위치 전송 대기');
    $('login-form').hidden = loggedIn;
    $('logged-in').hidden = !loggedIn;
    $('to-arrival').disabled = !loggedIn;
    if (!loggedIn) {
        $('send').textContent = '로그인 전';
        return;
    }
    $('login-id').textContent = state.loginId;
    const url = `${config.clientUrl}/driver#token=${encodeURIComponent(state.token)}&sim=1`;
    $('qr-link').href = url;
    // 화면 글자에는 토큰을 가린다. QR과 링크 주소에는 그대로 담는다.
    $('qr-link').textContent = `${config.clientUrl}/driver#token=••••&sim=1`;
    await QRCode.toCanvas($('qr'), url, { width: 112, margin: 1 });
}

$('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    $('auth-error').textContent = '';
    try {
        state.token = await login(form.get('loginId'), form.get('password'));
        state.loginId = form.get('loginId');
        writeStored(TOKEN_KEY, state.token);
        writeStored(LOGIN_ID_KEY, state.loginId);
        event.target.reset();
        await showLoggedIn();
    } catch (error) {
        $('auth-error').textContent = `로그인 실패: ${error.message}`;
    }
});

$('logout').addEventListener('click', () => {
    state.token = null;
    state.loginId = null;
    writeStored(TOKEN_KEY, null);
    writeStored(LOGIN_ID_KEY, null);
    showLoggedIn();
});

// ---------- 이동 ----------
function mode() {
    return document.querySelector('input[name="mode"]:checked').value;
}

function setMode(value) {
    document.querySelector(`input[name="mode"][value="${value}"]`).checked = true;
}

function message(text) {
    $('move-msg').textContent = text;
}

function placeCar(latlng) {
    state.car = { lat: latlng.lat, lng: latlng.lng };
    carMarker.setLatLng(latlng).addTo(map);
    updatePosition();
}

async function routeTo(dest) {
    if (!state.car) {
        message('출발지를 먼저 찍으세요.');
        return;
    }
    message('경로를 찾는 중…');
    try {
        const { points, distance } = await fetchRoute(state.car, dest);
        state.path = createPath([state.car, ...points]);
        state.travelled = 0;
        routeLine.setLatLngs(points.map((p) => [p.lat, p.lng]));
        destMarker.setLatLng(dest).addTo(map);
        state.destCell = cellOf(dest.lat, dest.lng);
        redrawRange();
        $('toggle').disabled = false;
        message(`도로 경로 ${(distance / 1000).toFixed(2)}km. 출발을 누르세요.`);
        updatePosition();
    } catch (error) {
        message(`경로를 찾지 못했습니다: ${error.message}. 길찾기 서버(/osrm)에 닿는지 확인하세요.`);
    }
}

map.on('click', (event) => {
    if (state.running) return;
    if (mode() === 'start') {
        clearRoute();
        state.destCell = null;
        redrawRange();
        placeCar(event.latlng);
        setMode('dest');
        message('목적지를 찍으세요.');
    } else {
        routeTo(event.latlng);
    }
});

function clearRoute() {
    state.path = null;
    routeLine.setLatLngs([]);
    destMarker.remove();
    $('toggle').disabled = true;
}

function setRunning(running) {
    state.running = running;
    $('toggle').textContent = running ? '멈춤' : '출발';
}

$('toggle').addEventListener('click', () => {
    if (!state.path) return;
    setRunning(!state.running);
    message(state.running ? '이동 중입니다.' : '멈췄습니다. 출발을 누르면 이어 갑니다.');
    lastTick = performance.now();
});

// requestAnimationFrame은 창이 가려지면 멈춘다. 진행자가 다른 창을 봐도 차가 계속 가도록 타이머로 움직인다.
// 가려진 탭의 타이머는 1초 간격으로 늦춰지므로 흐른 시간만큼 움직이되, 한 번에 2초까지만 반영한다.
let lastTick = 0;
setInterval(() => {
    if (!state.running) return;
    const now = performance.now();
    const seconds = Math.min((now - lastTick) / 1000, 2);
    lastTick = now;
    state.travelled += BASE_SPEED_MPS * Number(document.querySelector('input[name="speed"]:checked').value) * seconds;
    state.car = state.path.at(state.travelled);
    carMarker.setLatLng([state.car.lat, state.car.lng]);
    updatePosition();
    if (state.travelled >= state.path.length) {
        setRunning(false);
        clearRoute();
        message('도착했습니다. 다음 목적지를 찍으세요.');
    }
}, 100);

// 서버 계약에 도착 칸이 없어 도착 주소만 보여 주고, 진행자가 그 주소를 보고 지도에서 목적지를 찍는다.
$('to-arrival').addEventListener('click', async () => {
    const list = $('arrivals');
    try {
        const items = await fetchInProgressShipments(state.token);
        list.replaceChildren(...items.map((item) => {
            const li = document.createElement('li');
            li.textContent = `#${item.deliveryId} ${item.deliveryAddress} (${item.status})`;
            return li;
        }));
        if (!items.length) list.textContent = '진행 중 배차가 없습니다. 폰에서 콜을 하나 수락하세요.';
        setMode('dest');
    } catch (error) {
        list.textContent = `진행 중 배차 조회 실패: ${error.message}`;
    }
});

// ---------- 현재 위치 표시 ----------
function updatePosition() {
    if (!state.car) return;
    const cell = cellOf(state.car.lat, state.car.lng);
    const cellKey = key(cell);
    $('lat').textContent = state.car.lat.toFixed(6);
    $('lng').textContent = state.car.lng.toFixed(6);
    $('cell').textContent = `${cellCode(cell)} (${cell.x}, ${cell.y})${cellLayers.has(cellKey) ? '' : ' 영역 밖'}`;
    $('cell-big').textContent = `${cell.x}, ${cell.y}`;
    $('remain').textContent = state.path ? `${Math.max(0, state.path.length - state.travelled).toFixed(0)}m` : '-';
    if (cellKey !== state.currentCellKey) {
        currentCellLayer.setBounds(cellBounds(cell)).addTo(map);
        state.currentCellKey = cellKey;
        // 빠르게 달리면 1.5초 사이에 칸 하나를 통째로 지나칠 수 있어, 칸이 바뀌면 바로 보낸다
        sendTick();
    }
}

// ---------- 위치 전송 ----------
async function sendTick() {
    if (!state.token || !state.car) return;
    if (state.sending) {
        state.resend = true;
        return;
    }
    state.sending = true;
    const at = new Date().toTimeString().slice(0, 8);
    const sent = state.car;
    try {
        const { cell } = await sendLocation(state.token, sent);
        $('send').textContent = `${at} 성공`;
        setBadge('badge-send', 'ok', '위치 전송 중');
        const local = cellCode(cellOf(sent.lat, sent.lng));
        $('server-cell').textContent = cell === local ? `${cell}` : `${cell} (화면 칸과 다름)`;
        if (cell !== state.serverCell) {
            state.serverCell = cell;
            refreshBundle();
        }
    } catch (error) {
        const expired = error instanceof HttpError && error.status === 401;
        $('send').textContent = expired
            ? `${at} 401: 토큰이 끝났습니다. 다시 로그인하세요(폰에서 따로 로그인하면 이렇게 됩니다)`
            : `${at} 실패: ${error.message}`;
        setBadge('badge-send', 'fail', expired ? '토큰 끝남 · 다시 로그인' : '위치 전송 실패');
    } finally {
        state.sending = false;
        if (state.resend) {
            state.resend = false;
            sendTick();
        }
    }
}
setInterval(sendTick, SEND_INTERVAL_MS);

// 폰 화면에 콜이 떠야 하는지 진행자가 알 수 있게 서버가 고른 후보 건수를 보여 준다.
async function refreshBundle() {
    try {
        const { cell, items } = await fetchBundleCandidates(state.token);
        $('bundle').textContent = items.length
            ? `${items.length}건 (${items.map((item) => `#${item.deliveryId}`).join(', ')})`
            : '0건';
        showBundle(items);
        if (cell != null) {
            state.bundle = { cell, items };
            markCandidates(cell, items);
            drawRange(cell, items);
        }
    } catch (error) {
        $('bundle').textContent = `조회 실패: ${error.message}`;
    }
}
// 칸이 그대로여도 새 콜이 들어오거나 수락돼 후보가 바뀔 수 있다
setInterval(() => state.serverCell != null && refreshBundle(), 10_000);

// ---------- 후보 출발 칸과 도착 범위 ----------
// 서버는 콜 위치를 주지 않으므로 콜마다 점을 찍지 않는다. bundle 응답의 칸(= 후보가 출발하는 칸)과
// 진행자가 찍은 목적지 칸 + 이웃 8칸(서버가 후보를 고르는 도착 범위와 같은 모양)을 칠하고 둘을 화살표로 잇는다.
const rangeLayer = L.layerGroup().addTo(map);
const legend = L.control({ position: 'bottomleft' });
legend.onAdd = () => {
    const box = L.DomUtil.create('div', 'legend');
    box.innerHTML = '<span><i class="key key--start"></i>후보 출발 칸</span><span><i class="key key--range"></i>후보 도착 범위</span><span><i class="key key--route"></i>내 경로</span>';
    return box;
};
legend.addTo(map);
const KM_X = 111.32 * Math.cos((37.55 * Math.PI) / 180);
const KM_Y = 110.95;

function rangeBounds(cell) {
    return [cellBounds({ x: cell.x - 1, y: cell.y - 1 })[0], cellBounds({ x: cell.x + 1, y: cell.y + 1 })[1]];
}

// 출발 칸 가운데에서 도착 범위 가운데로 가는 직선이 범위 가장자리와 만나는 점
function edgePoint(from, [[s, w], [n, e]]) {
    const to = { lat: (s + n) / 2, lng: (w + e) / 2 };
    const dLat = from.lat - to.lat;
    const dLng = from.lng - to.lng;
    const t = Math.min(dLat ? Math.abs((n - s) / 2 / dLat) : Infinity, dLng ? Math.abs((e - w) / 2 / dLng) : Infinity, 1);
    return { lat: to.lat + dLat * t, lng: to.lng + dLng * t };
}

// km 평면에서 2차 곡선과 화살촉을 만든 뒤 위경도로 되돌린다
function curvedArrow(from, to) {
    const P = (p) => ({ x: p.lng * KM_X, y: p.lat * KM_Y });
    const Q = (p) => [p.y / KM_Y, p.x / KM_X];
    const a = P(from), b = P(to);
    const dx = b.x - a.x, dy = b.y - a.y, len = Math.hypot(dx, dy);
    const c = { x: (a.x + b.x) / 2 - dy * 0.22, y: (a.y + b.y) / 2 + dx * 0.22 };
    const line = [];
    for (let i = 0; i <= 24; i++) {
        const t = i / 24;
        line.push(Q({ x: (1 - t) ** 2 * a.x + 2 * (1 - t) * t * c.x + t ** 2 * b.x, y: (1 - t) ** 2 * a.y + 2 * (1 - t) * t * c.y + t ** 2 * b.y }));
    }
    const ux = b.x - c.x, uy = b.y - c.y, ul = Math.hypot(ux, uy) || 1;
    const size = Math.min(0.09, len * 0.25);
    const back = { x: b.x - (ux / ul) * size, y: b.y - (uy / ul) * size };
    const head = [Q(b), Q({ x: back.x - (uy / ul) * size * 0.6, y: back.y + (ux / ul) * size * 0.6 }), Q({ x: back.x + (uy / ul) * size * 0.6, y: back.y - (ux / ul) * size * 0.6 })];
    return { line, head };
}

// 목적지를 바꾸면 다음 후보 조회(최대 10초)를 기다리지 않고 마지막 응답으로 범위를 바로 다시 그린다
function redrawRange() {
    if (state.bundle) drawRange(state.bundle.cell, state.bundle.items);
}

function drawRange(code, items) {
    rangeLayer.clearLayers();
    const note = $('range-note');
    if (!items.length) {
        note.textContent = '';
        return;
    }
    if (!state.destCell) {
        note.textContent = '목적지를 찍으면 도착 범위가 보입니다.';
        return;
    }
    note.textContent = '모두 이 칸에서 출발해 도착 근처 범위로 갑니다.';
    const range = rangeBounds(state.destCell);
    L.rectangle(range, { color: BRAND, weight: 1.5, opacity: 0.6, dashArray: '6 5', fillColor: BRAND, fillOpacity: 0.1, interactive: false }).addTo(rangeLayer);
    L.rectangle(cellBounds(state.destCell), { color: BRAND, weight: 2.5, opacity: 0.85, fillColor: BRAND, fillOpacity: 0.12, interactive: false }).addTo(rangeLayer);
    const from = cellBounds(cellFromCode(code));
    const start = { lat: (from[0][0] + from[1][0]) / 2, lng: (from[0][1] + from[1][1]) / 2 };
    const { line, head } = curvedArrow(start, edgePoint(start, range));
    L.polyline(line, { color: BRAND, weight: 5, opacity: 0.9, lineCap: 'round', interactive: false }).addTo(rangeLayer);
    L.polygon(head, { color: BRAND, weight: 1, fillColor: BRAND, fillOpacity: 0.95, interactive: false }).addTo(rangeLayer);
}

// ---------- 상태 표시 ----------
function setBadge(id, tone, text) {
    $(id).className = tone ? `badge badge--${tone}` : 'badge';
    $(id).textContent = text;
}

// 지금 칸의 묶음 후보를 크게 건수로, 아래에 상품명과 배송비를 보여 준다
function showBundle(items) {
    $('bundle-big').textContent = items == null ? '-' : `${items.length}건`;
    $('candidates').replaceChildren(...(items ?? []).map((item) => {
        const li = document.createElement('li');
        const name = document.createElement('span');
        name.textContent = item.itemName;
        const fee = document.createElement('b');
        fee.textContent = `${item.deliveryFee.toLocaleString('ko-KR')}원`;
        li.append(name, fee);
        return li;
    }));
}

// ---------- 후보 칸 표시 ----------
// 서버에 전체 대기 콜 위치를 묻는 API는 없으므로(시뮬레이터 전용 코드 금지) 묶음 후보 응답으로 지금 칸만 강조하고,
// 후보가 떴던 칸은 지나간 뒤에도 옅게 남긴다.
const candidateCells = new Map();
let activeCandidateCell = null;

function drawCandidateCell(code, active) {
    let rect = candidateCells.get(code);
    if (!rect) {
        rect = L.rectangle(cellBounds(cellFromCode(code)), { interactive: false }).addTo(map);
        candidateCells.set(code, rect);
    }
    rect.setStyle(active
        ? { color: BRAND, weight: 3, opacity: 1, fillColor: BRAND, fillOpacity: 0.38 }
        : { color: BRAND, weight: 1, opacity: 0.35, fillColor: BRAND, fillOpacity: 0.08 });
}

function markCandidates(code, items) {
    if (activeCandidateCell != null && (activeCandidateCell !== code || !items.length)) {
        drawCandidateCell(activeCandidateCell, false);
        activeCandidateCell = null;
    }
    if (!items.length) return;
    drawCandidateCell(code, true);
    activeCandidateCell = code;
}

function clearCandidates() {
    for (const rect of candidateCells.values()) rect.remove();
    rangeLayer.clearLayers();
    state.bundle = null;
    candidateCells.clear();
    activeCandidateCell = null;
}

// ---------- 시작 ----------
// 토큰을 localStorage에 두던 이전 판이 남긴 값을 지운다
try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(LOGIN_ID_KEY);
} catch {
    // 저장소가 막혀 있으면 지울 것도 없다
}
$('target').textContent = `대상 서버 ${config.apiTarget} · 폰 주소 ${config.clientUrl}`;
state.token = readStored(TOKEN_KEY);
state.loginId = readStored(LOGIN_ID_KEY);
showLoggedIn();
message('지도에서 출발지를 찍으세요.');
