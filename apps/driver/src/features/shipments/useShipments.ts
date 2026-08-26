import { useCallback, useEffect, useRef, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { deliveryApi } from '@/api/deliveryApi';
import { ShipmentSummaryResponse } from '@/model/delivery';

/**
 * 내 배차 목록 상태. 최초 로드 + 당겨서 새로고침 + 탭 재진입 시 재조회.
 * (요청을 수락하면 목에서 내 배차로 이동하므로, 탭을 다시 열면 반영된다.)
 * 내 배차와 정산 화면이 함께 사용한다.
 */
export function useShipments() {
  const [items, setItems] = useState<ShipmentSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setItems(await deliveryApi.getShipments());
    } catch (e) {
      setError(e instanceof Error ? e.message : '내 배차를 불러오지 못했어요');
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

  const firstFocus = useRef(true);
  useFocusEffect(
    useCallback(() => {
      if (firstFocus.current) {
        firstFocus.current = false;
        return;
      }
      void refresh();
    }, [refresh]),
  );

  return { items, loading, refreshing, error, refresh };
}
