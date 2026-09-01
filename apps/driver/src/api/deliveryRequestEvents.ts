import { fetch } from 'expo/fetch';
import { AppState } from 'react-native';
import { config } from '@/lib/config';
import { notifyUnauthorized } from '@/lib/http';
import { tokenStore } from '@/lib/tokenStore';

const EVENTS_PATH = '/api/delivery/requests/events';
const INITIAL_RECONNECT_DELAY_MS = 1_000;
const MAX_RECONNECT_DELAY_MS = 30_000;
const REQUESTS_CHANGED_EVENT = 'delivery-requests-changed';

/** 요청 목록 화면이 활성화된 동안 SSE 변경 신호를 수신한다. */
export function subscribeDeliveryRequestEvents(onRequestsChanged: () => void): () => void {
  if (config.useMock) return () => undefined;

  let closed = false;
  // 앱 초기화 직후에는 currentState가 null일 수 있다. 그때도 최초 연결은 시작한다.
  let appIsActive = AppState.currentState === null || AppState.currentState === 'active';
  let controller: AbortController | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
  let refreshQueued = false;

  const clearReconnectTimer = () => {
    if (reconnectTimer === null) return;
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  };

  const abortConnection = () => {
    const current = controller;
    controller = null;
    current?.abort();
  };

  const notifyChanged = () => {
    if (refreshQueued) return;
    refreshQueued = true;
    void Promise.resolve().then(() => {
      refreshQueued = false;
      if (!closed && appIsActive) onRequestsChanged();
    });
  };

  let connect: () => Promise<void>;

  const scheduleReconnect = () => {
    if (closed || !appIsActive || reconnectTimer !== null) return;

    const delayMs = reconnectDelayMs;
    reconnectDelayMs = Math.min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      void connect();
    }, delayMs);
  };

  connect = async (): Promise<void> => {
    if (closed || !appIsActive || controller !== null) return;

    const token = tokenStore.get();
    if (!token) return;

    const nextController = new AbortController();
    controller = nextController;
    let shouldReconnect = true;

    try {
      const response = await fetch(config.apiBaseUrl + EVENTS_PATH, {
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        },
        signal: nextController.signal,
      });

      if (response.status === 401) {
        notifyUnauthorized();
        shouldReconnect = false;
        return;
      }
      if (!response.ok || response.body === null) return;

      reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
      // 재연결 사이의 변경을 놓칠 수 있으므로, 연결될 때마다 현재 목록을 다시 조회한다.
      notifyChanged();
      await readSseEvents(response.body, (eventName) => {
        if (eventName === REQUESTS_CHANGED_EVENT) notifyChanged();
      });
    } catch {
      // abort()는 화면 이탈·백그라운드 전환의 정상 종료 경로다.
      // 실제 연결 오류는 finally에서 재연결한다.
    } finally {
      if (controller === nextController) controller = null;
      if (shouldReconnect && !nextController.signal.aborted) scheduleReconnect();
    }
  };

  const appStateSubscription = AppState.addEventListener('change', (nextState) => {
    appIsActive = nextState === 'active';
    if (!appIsActive) {
      clearReconnectTimer();
      abortConnection();
      return;
    }

    reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
    void connect();
  });

  void connect();

  return () => {
    closed = true;
    clearReconnectTimer();
    abortConnection();
    appStateSubscription.remove();
  };
}

async function readSseEvents(
  body: ReadableStream<Uint8Array>,
  onEvent: (eventName: string) => void,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      buffer = consumeEvents(buffer, onEvent);
    }
    buffer += decoder.decode();
    consumeEvents(buffer, onEvent);
  } finally {
    reader.releaseLock();
  }
}

function consumeEvents(buffer: string, onEvent: (eventName: string) => void): string {
  while (true) {
    const boundary = /\r?\n\r?\n/.exec(buffer);
    if (boundary?.index === undefined) return buffer;

    const event = buffer.slice(0, boundary.index);
    buffer = buffer.slice(boundary.index + boundary[0].length);
    const eventName = eventNameOf(event);
    if (eventName !== null) onEvent(eventName);
  }
}

function eventNameOf(event: string): string | null {
  let eventName = 'message';
  let hasData = false;

  for (const line of event.split(/\r?\n/)) {
    if (line.startsWith(':')) continue;
    const separator = line.indexOf(':');
    const field = separator === -1 ? line : line.slice(0, separator);
    const value = separator === -1 ? '' : line.slice(separator + 1).replace(/^ /, '');
    if (field === 'event') eventName = value;
    if (field === 'data') hasData = true;
  }

  return hasData ? eventName : null;
}
