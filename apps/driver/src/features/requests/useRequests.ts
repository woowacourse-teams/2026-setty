import { useCallback, useEffect, useRef, useState } from 'react';
import { deliveryApi } from '@/api/deliveryApi';
import { errorMessage } from '@/lib/errorMessage';
import { DeliveryRequestSummaryResponse } from '@/model/delivery';
import { rejectedStore } from './rejectedStore';

/**
 * 요청 목록 상태. 최초 로드 + 당겨서 새로고침 + 로컬 거절(숨김)을 다룬다.
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

  const load = useCallback(async (highlightNewRequests = false) => {
    const loadId = ++latestLoad.current;
    setError(null);
    try {
      // 로컬 거절한 요청은 서버에 남아 있어도 목록에서 숨긴다.
      const requests = rejectedStore.filter(await deliveryApi.getRequests());
      if (loadId !== latestLoad.current) return;

      if (highlightNewRequests) {
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
      if (loadId === latestLoad.current) {
        setError(errorMessage(e, '요청 목록을 불러오지 못했어요'));
      }
    }
  }, []);

  useEffect(() => {
    void load().finally(() => {
      initialLoadFinished.current = true;
      setLoading(false);
    });
  }, [load]);

  const refresh = useCallback(async () => {
    const refreshId = ++latestRefresh.current;
    setRefreshing(true);
    try {
      await load();
    } finally {
      if (refreshId === latestRefresh.current) setRefreshing(false);
    }
  }, [load]);

  // SSE 신호는 화면에 새로고침 상태를 노출하지 않고 목록만 다시 맞춘다.
  const reload = useCallback(async () => {
    await load(initialLoadFinished.current);
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
