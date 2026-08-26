import { useCallback, useEffect, useState } from 'react';
import { deliveryApi } from '@/api/deliveryApi';
import { DeliveryRequestDetailResponse } from '@/model/delivery';

/** 수신(요청 단건) 상세 상태 + 수락 액션. */
export function useRequestDetail(deliveryId: number) {
  const [detail, setDetail] = useState<DeliveryRequestDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [accepting, setAccepting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setDetail(await deliveryApi.getRequest(deliveryId));
    } catch (e) {
      setError(e instanceof Error ? e.message : '요청을 불러오지 못했어요');
    } finally {
      setLoading(false);
    }
  }, [deliveryId]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 수락. 성공하면 true(호출부에서 목록 재조회 후 이동). */
  const accept = useCallback(async (): Promise<boolean> => {
    setAccepting(true);
    try {
      await deliveryApi.acceptRequest(deliveryId);
      return true;
    } catch {
      setError('수락에 실패했어요. 다시 시도해 주세요');
      return false;
    } finally {
      setAccepting(false);
    }
  }, [deliveryId]);

  return { detail, loading, accepting, error, accept };
}
