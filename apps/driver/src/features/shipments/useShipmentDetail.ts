import { useCallback, useEffect, useState } from 'react';
import { deliveryApi } from '@/api/deliveryApi';
import { errorMessage } from '@/lib/errorMessage';
import { ShipmentDetailResponse } from '@/model/delivery';
import { rejectedStore } from '@/features/requests/rejectedStore';

export interface AdvanceResult {
  ok: boolean;
  message: string;
}

/**
 * 내 배차 상세 + 상태 전이(수령/완료).
 * 다음 상태로 넘기는 버튼 하나로 ACCEPTED→PICKED_UP→DELIVERED를 진행한다.
 * 서버 가드 위반(409)이면 메시지를 돌려주고 상세를 재조회해 동기화한다.
 */
export function useShipmentDetail(deliveryId: number) {
  const [detail, setDetail] = useState<ShipmentDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setDetail(await deliveryApi.getShipment(deliveryId));
    } catch (e) {
      setError(errorMessage(e, '배차를 불러오지 못했어요'));
    } finally {
      setLoading(false);
    }
  }, [deliveryId]);

  useEffect(() => {
    void load();
  }, [load]);

  const advance = useCallback(async (): Promise<AdvanceResult> => {
    if (!detail) return { ok: false, message: '' };
    setWorking(true);
    try {
      if (detail.status === 'ACCEPTED') {
        await deliveryApi.pickupShipment(deliveryId);
        await load();
        return { ok: true, message: '수령 완료! 안전하게 배송해요' };
      }
      if (detail.status === 'PICKED_UP') {
        await deliveryApi.completeShipment(deliveryId);
        await load();
        return { ok: true, message: '배송 완료! 오늘도 고생했어요' };
      }
      return { ok: false, message: '' };
    } catch (e) {
      await load(); // 서버 상태와 다시 맞춘다
      return { ok: false, message: errorMessage(e, '상태를 변경하지 못했어요') };
    } finally {
      setWorking(false);
    }
  }, [detail, deliveryId, load]);

  const cancel = useCallback(async (): Promise<AdvanceResult> => {
    // 성공하면 화면을 닫으므로 cancelling을 풀지 않아 닫히기 전까지 버튼을 막는다.
    setCancelling(true);
    try {
      await deliveryApi.cancelShipment(deliveryId);
      // 거절과 같이 취소한 요청은 이번 세션 동안 홈 목록에서 숨긴다.
      rejectedStore.add(deliveryId);
      return { ok: true, message: '배차를 취소했어요' };
    } catch (e) {
      await load();
      setCancelling(false);
      return { ok: false, message: errorMessage(e, '배차를 취소하지 못했어요') };
    }
  }, [deliveryId, load]);

  return { detail, loading, working, cancelling, error, advance, cancel };
}
