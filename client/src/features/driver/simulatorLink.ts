import { deliverySimulatorStore, deliveryTokenStore } from '../../api/http';
import { parseSimulatorLink } from '../../model/driverLocation';

/**
 * 시뮬레이터 QR(`/driver#token=…&sim=1`)의 토큰으로 로그인하고 주소창에서 토큰을 지운다. 처리했으면 true.
 * 서버 로그인을 다시 하지 않으므로 시뮬레이터와 이 탭이 같은 토큰을 쓴다(로그인마다 토큰이 바뀌어 서로 끊지 않게).
 */
export function applySimulatorLink(): boolean {
    const link = parseSimulatorLink(window.location.hash);
    if (!link) return false;
    deliveryTokenStore.set(link.token);
    if (link.simulator) deliverySimulatorStore.set();
    // 라우터가 history.state에 둔 위치 키를 지우지 않도록 지금 상태를 그대로 넘긴다
    window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
    return true;
}

/**
 * 처음 열 때와, 이미 `/driver`가 열린 탭에서 QR을 다시 찍어 `#` 뒤만 바뀔 때 모두 처리한다.
 * 뒤의 경우 페이지가 다시 로드되지 않아 화면·실시간 연결이 이전 토큰으로 붙어 있으므로 새 토큰으로 다시 연다.
 */
export function listenSimulatorLinks() {
    applySimulatorLink();
    window.addEventListener('hashchange', () => {
        if (applySimulatorLink()) window.location.reload();
    });
}
