import { useCallback, useEffect, useState } from 'react';
import { deliveryApi } from '@/api/deliveryApi';
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

  const load = useCallback(async () => {
    setError(null);
    try {
      // 로컬 거절한 요청은 서버에 남아 있어도 목록에서 숨긴다.
      setItems(rejectedStore.filter(await deliveryApi.getRequests()));
    } catch (e) {
      setError(e instanceof Error ? e.message : '요청 목록을 불러오지 못했어요');
    }
  }, []);

  useEffect(() => {
    void load().finally(() => setLoading(false));
  }, [load]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  const rejectLocal = useCallback((deliveryId: number) => {
    rejectedStore.add(deliveryId);
    setItems((prev) => prev.filter((r) => r.deliveryId !== deliveryId));
  }, []);

  return { items, loading, refreshing, error, refresh, rejectLocal };
}
