import type {
    DeliveryRequestDetail,
    DeliverySignUpForm,
    DeliverySummary,
    ShipmentDetail
} from '../model/delivery';
import { apiCall, deliveryLoginIdStore, deliveryTokenStore } from './http';

const delivery = { auth: 'delivery' } as const;

export const deliveryRepository = {
    async login(loginId: string, password: string) {
        const result = await apiCall<{ token: string }>('/api/delivery/auth/login', { method: 'POST', body: { loginId, password } });
        deliveryTokenStore.set(result.token);
        deliveryLoginIdStore.set(loginId);
    },

    async signUp(form: DeliverySignUpForm) {
        await apiCall('/api/delivery/auth/signup', { method: 'POST', body: form });
    },

    getRequests(): Promise<DeliverySummary[]> {
        return apiCall('/api/delivery/requests', delivery);
    },

    getRequest(deliveryId: number): Promise<DeliveryRequestDetail> {
        return apiCall(`/api/delivery/requests/${deliveryId}`, delivery);
    },

    async accept(deliveryId: number) {
        await apiCall(`/api/delivery/requests/${deliveryId}`, { ...delivery, method: 'POST' });
    },

    getShipments(): Promise<DeliverySummary[]> {
        return apiCall('/api/delivery/shipments', delivery);
    },

    getShipment(deliveryId: number): Promise<ShipmentDetail> {
        return apiCall(`/api/delivery/shipments/${deliveryId}`, delivery);
    },

    async pickUp(deliveryId: number) {
        await apiCall(`/api/delivery/shipments/${deliveryId}/pickup`, { ...delivery, method: 'POST' });
    },

    /** 픽업 전에만 취소할 수 있고, 취소하면 다른 배송원에게 요청이 다시 열린다. */
    async cancel(deliveryId: number) {
        await apiCall(`/api/delivery/shipments/${deliveryId}/cancellation`, { ...delivery, method: 'POST' });
    },

    async complete(deliveryId: number) {
        await apiCall(`/api/delivery/shipments/${deliveryId}/completion`, { ...delivery, method: 'POST' });
    }
};

const REQUESTS_CHANGED_EVENT = 'delivery-requests-changed';
const INITIAL_RECONNECT_DELAY_MILLIS = 1_000;
const MAX_RECONNECT_DELAY_MILLIS = 30_000;

/**
 * 배차 요청 목록이 바뀌면 알린다. 화면이 보이는 동안만 서버 SSE에 연결하고, 끊기면 점점 늦게 다시 연결한다.
 * EventSource는 인증 헤더를 붙일 수 없어 fetch 응답 본문을 직접 읽는다.
 * 연결할 때마다 한 번 알려 끊긴 사이의 변경도 다시 불러오게 한다.
 * 서버는 배송원 한 명당 연결 수를 제한하므로(초과하면 429) 연결되지 않은 동안은 [onLiveChange]로 알려 화면이 대신 주기적으로 불러오게 한다.
 */
export function subscribeRequestEvents(
    onChanged: () => void,
    onUnauthorized: () => void,
    onLiveChange: (isLive: boolean) => void
): () => void {
    let closed = false;
    let controller: AbortController | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let reconnectDelay = INITIAL_RECONNECT_DELAY_MILLIS;

    const isVisible = () => document.visibilityState === 'visible';

    const disconnect = () => {
        if (reconnectTimer !== null) clearTimeout(reconnectTimer);
        reconnectTimer = null;
        controller?.abort();
        controller = null;
    };

    const scheduleReconnect = () => {
        if (closed || !isVisible() || reconnectTimer !== null) return;
        reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            void connect();
        }, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY_MILLIS);
    };

    const connect = async () => {
        const token = deliveryTokenStore.get();
        if (closed || !isVisible() || controller !== null || !token) return;
        const current = new AbortController();
        controller = current;
        let shouldReconnect = true;
        try {
            const response = await fetch('/api/delivery/requests/events', {
                headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
                signal: current.signal
            });
            if (response.status === 401) {
                shouldReconnect = false;
                onUnauthorized();
                return;
            }
            if (!response.ok || !response.body) return;
            reconnectDelay = INITIAL_RECONNECT_DELAY_MILLIS;
            onLiveChange(true);
            onChanged();
            await readEvents(response.body, (eventName) => {
                if (eventName === REQUESTS_CHANGED_EVENT) onChanged();
            });
        } catch {
            // 화면을 떠나거나 숨겨 연결을 끊은 경우도 여기로 온다
        } finally {
            onLiveChange(false);
            if (controller === current) controller = null;
            if (shouldReconnect && !current.signal.aborted) scheduleReconnect();
        }
    };

    const onVisibilityChange = () => {
        if (isVisible()) {
            reconnectDelay = INITIAL_RECONNECT_DELAY_MILLIS;
            void connect();
        } else {
            disconnect();
        }
    };

    document.addEventListener('visibilitychange', onVisibilityChange);
    void connect();
    return () => {
        closed = true;
        disconnect();
        document.removeEventListener('visibilitychange', onVisibilityChange);
    };
}

async function readEvents(body: ReadableStream<Uint8Array>, onEvent: (eventName: string) => void) {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    try {
        for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            for (;;) {
                const boundary = /\r?\n\r?\n/.exec(buffer);
                if (!boundary) break;
                const eventName = eventNameOf(buffer.slice(0, boundary.index));
                buffer = buffer.slice(boundary.index + boundary[0].length);
                if (eventName !== null) onEvent(eventName);
            }
        }
    } finally {
        reader.releaseLock();
    }
}

/** SSE 이벤트 하나의 이름. 데이터가 없는 주석·keep-alive면 null. */
function eventNameOf(block: string): string | null {
    let eventName = 'message';
    let hasData = false;
    for (const line of block.split(/\r?\n/)) {
        if (line.startsWith(':')) continue;
        const separator = line.indexOf(':');
        const field = separator === -1 ? line : line.slice(0, separator);
        const value = separator === -1 ? '' : line.slice(separator + 1).replace(/^ /, '');
        if (field === 'event') eventName = value;
        if (field === 'data') hasData = true;
    }
    return hasData ? eventName : null;
}
