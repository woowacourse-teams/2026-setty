import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import QRCode from 'qrcode';
import { HttpError, fetchBundleCandidates, fetchInProgressShipments, fetchRoute, login, sendLocation } from './api.js';
import { cellBounds, cellCode, cellOf } from './grid.js';
import { createPath } from './route.js';

const config = __SIM_CONFIG__;
const BASE_SPEED_MPS = 30_000 / 3600;
const SEND_INTERVAL_MS = 1500;
const TOKEN_KEY = 'delivery-sim:token';
const LOGIN_ID_KEY = 'delivery-sim:login-id';
const SAGE = '#798777';
const CLAY = '#A65E44';

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
    serverCell: null
};

// ---------- 지도와 격자 ----------
const [south, west, cols, rows] = config.area;
const origin = cellOf(south, west);
const areaCells = [];
for (let dy = 0; dy < rows; dy++) {
    for (let dx = 0; dx < cols; dx++) areaCells.push({ x: origin.x + dx, y: origin.y + dy });
}

const map = L.map('map');
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

const cellLayers = new Map();
for (const cell of areaCells) {
    const rect = L.rectangle(cellBounds(cell), { color: SAGE, weight: 1, fillOpacity: 0.04 }).addTo(map);
    rect.bindTooltip(`칸 ${cellCode(cell)} (${cell.x}, ${cell.y})`, { sticky: true });
    cellLayers.set(key(cell), rect);
}
map.fitBounds(L.latLngBounds(areaCells.flatMap(cellBounds)));

const routeLine = L.polyline([], { color: CLAY, weight: 4, opacity: 0.7 }).addTo(map);
const carMarker = L.circleMarker([0, 0], { radius: 9, color: '#fff', weight: 3, fillColor: CLAY, fillOpacity: 1 });
const destMarker = L.circleMarker([0, 0], { radius: 7, color: CLAY, weight: 3, fillColor: '#fff', fillOpacity: 1 });

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
    $('server-cell').textContent = '-';
    $('bundle').textContent = '-';
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
    await QRCode.toCanvas($('qr'), url, { width: 180, margin: 1 });
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
        $('toggle').disabled = false;
        message(`도로 경로 ${(distance / 1000).toFixed(2)}km. 출발을 누르세요.`);
        updatePosition();
    } catch (error) {
        message(`경로 실패: ${error.message} (OSRM이 떠 있는지 확인)`);
    }
}

map.on('click', (event) => {
    if (state.running) return;
    if (mode() === 'start') {
        clearRoute();
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
    state.travelled += BASE_SPEED_MPS * Number($('speed').value) * seconds;
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
    $('remain').textContent = state.path ? `${Math.max(0, state.path.length - state.travelled).toFixed(0)}m` : '-';
    if (cellKey !== state.currentCellKey) {
        cellLayers.get(state.currentCellKey)?.setStyle({ fillColor: SAGE, fillOpacity: 0.04 });
        cellLayers.get(cellKey)?.setStyle({ fillColor: CLAY, fillOpacity: 0.25 });
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
        const { items } = await fetchBundleCandidates(state.token);
        $('bundle').textContent = items.length
            ? `${items.length}건 (${items.map((item) => `#${item.deliveryId}`).join(', ')})`
            : '0건';
    } catch (error) {
        $('bundle').textContent = `조회 실패: ${error.message}`;
    }
}
// 칸이 그대로여도 새 콜이 들어오거나 수락돼 후보가 바뀔 수 있다
setInterval(() => state.serverCell != null && refreshBundle(), 10_000);

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
