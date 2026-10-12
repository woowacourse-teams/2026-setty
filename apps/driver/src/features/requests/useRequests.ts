import { useCallback, useEffect, useRef, useState } from 'react';
import { deliveryApi } from '@/api/deliveryApi';
import { errorMessage } from '@/lib/errorMessage';
import { DeliveryRequestSummaryResponse } from '@/model/delivery';
import { rejectedStore } from './rejectedStore';

interface RequestLoad {
  loadId: number;
  highlightNewRequests: boolean;
  promise: Promise<void>;
  complete: () => void;
}

/**
 * 요청 목록 상태. 최초 조회·새로고침·SSE 재조회는 동시 1건과 대기 1건으로 합친다.
 * 거절은 대응 API가 없어 로컬 목록에서만 제거한다(서버 미반영 — apps/docs 참고).
 */
export function useRequests() {
  const [items, setItems] = useState<DeliveryRequestSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newRequestIds, setNewRequestIds] = useState<Set<number>>(() => new Set());
  const itemsRef = useRef<DeliveryRequestSummaryResponse[]>([]);
  const initialLoadFinished = useRef(false);
  const latestLoad = useRef(0);
  const latestRefresh = useRef(0);
  const mounted = useRef(true);
  const currentLoad = useRef<RequestLoad | null>(null);
  const queuedLoad = useRef<RequestLoad | null>(null);

  const runLoad = useCallback(async function run(request: RequestLoad): Promise<void> {
    setError(null);
    try {
      // 로컬 거절한 요청은 서버에 남아 있어도 목록에서 숨긴다.
      const requests = rejectedStore.filter(await deliveryApi.getRequests());
      if (!mounted.current || request.loadId !== latestLoad.current) return;

      if (request.highlightNewRequests) {
        const previousRequestIds = new Set(itemsRef.current.map((request) => request.deliveryId));
        const addedRequestIds = requests
          .filter((request) => !previousRequestIds.has(request.deliveryId))
          .map((request) => request.deliveryId);
        if (addedRequestIds.length > 0) {
          setNewRequestIds((previousIds) => new Set([...previousIds, ...addedRequestIds]));
        }
      }

      itemsRef.current = requests;
      setItems(requests);
    } catch (e) {
      if (mounted.current && request.loadId === latestLoad.current) {
        setError(errorMessage(e, '요청 목록을 불러오지 못했어요'));
      }
    } finally {
      const next = queuedLoad.current;
      currentLoad.current = next;
      queuedLoad.current = null;
      request.complete();

      if (next !== null) {
        if (mounted.current) {
          void run(next);
        } else {
          currentLoad.current = null;
          next.complete();
        }
      }
    }
  }, []);

  const load = useCallback((highlightNewRequests = false): Promise<void> => {
    if (!mounted.current) return Promise.resolve();

    const loadId = ++latestLoad.current;
    if (queuedLoad.current !== null) {
      queuedLoad.current.loadId = loadId;
      queuedLoad.current.highlightNewRequests ||= highlightNewRequests;
      return queuedLoad.current.promise;
    }

    let complete!: () => void;
    const promise = new Promise<void>((resolve) => { complete = resolve; });
    const request: RequestLoad = {
      loadId,
      highlightNewRequests: highlightNewRequests || (currentLoad.current?.highlightNewRequests ?? false),
      promise,
      complete,
    };

    if (currentLoad.current !== null) {
      // 진행 중 도착한 변경은 버리지 않고, 다음 조회 한 건에 합친다.
      queuedLoad.current = request;
    } else {
      currentLoad.current = request;
      void runLoad(request);
    }
    return promise;
  }, [runLoad]);

  useEffect(() => {
    mounted.current = true;
    void load().finally(() => {
      if (!mounted.current) return;
      initialLoadFinished.current = true;
      setLoading(false);
    });

    return () => {
      mounted.current = false;
      queuedLoad.current?.complete();
      queuedLoad.current = null;
    };
  }, [load]);

  const refresh = useCallback(async () => {
    const refreshId = ++latestRefresh.current;
    setRefreshing(true);
    try {
      await load();
    } finally {
      if (mounted.current && refreshId === latestRefresh.current) setRefreshing(false);
    }
  }, [load]);

  // SSE는 조회만 예약한다. 조회가 지연돼도 알림마다 완료 대기자가 쌓이지 않게 한다.
  const reload = useCallback(() => {
    void load(initialLoadFinished.current);
  }, [load]);

  const clearNewRequestIds = useCallback(() => {
    setNewRequestIds(new Set());
  }, []);

  const rejectLocal = useCallback((deliveryId: number) => {
    rejectedStore.add(deliveryId);
    setItems((previousItems) => {
      const nextItems = previousItems.filter((request) => request.deliveryId !== deliveryId);
      itemsRef.current = nextItems;
      return nextItems;
    });
  }, []);

  return {
    items,
    loading,
    refreshing,
    error,
    newRequestIds,
    refresh,
    reload,
    clearNewRequestIds,
    rejectLocal,
  };
}
