import {
  DeliveryRequestDetailResponse,
  DeliveryRequestSummaryResponse,
  ShipmentDetailResponse,
  ShipmentSummaryResponse,
} from '@/model/delivery';
import { config } from '@/lib/config';
import { httpGet, httpGetList, httpPost } from '@/lib/http';
import { deliveryMock } from './mock/deliveryMock';

/**
 * 배차 API. useMock(베이스 URL 없음)이면 목으로, 아니면 실 fetch로 라우팅한다.
 * 이번 이슈(#173) 범위: 요청 목록 / 요청 상세 / 수락.
 * 수령·완료·내 배차 등은 후속 이슈에서 추가한다.
 *
 * 수락/수령/완료 POST는 응답 바디가 없다(204). 성공 후 화면에서 재조회한다.
 */
export const deliveryApi = {
  /** GET /api/delivery/requests */
  getRequests(): Promise<DeliveryRequestSummaryResponse[]> {
    return config.useMock
      ? deliveryMock.getRequests()
      : httpGetList<DeliveryRequestSummaryResponse>('/api/delivery/requests');
  },

  /** GET /api/delivery/requests/{deliveryId} */
  getRequest(deliveryId: number): Promise<DeliveryRequestDetailResponse> {
    return config.useMock
      ? deliveryMock.getRequest(deliveryId)
      : httpGet<DeliveryRequestDetailResponse>(
          `/api/delivery/requests/${deliveryId}`,
        );
  },

  /** POST /api/delivery/requests/{deliveryId} — 수락(응답 바디 없음) */
  async acceptRequest(deliveryId: number): Promise<void> {
    if (config.useMock) return deliveryMock.acceptRequest(deliveryId);
    await httpPost<void>(`/api/delivery/requests/${deliveryId}`);
  },

  /** GET /api/delivery/shipments — 내 배차 목록 */
  getShipments(): Promise<ShipmentSummaryResponse[]> {
    return config.useMock
      ? deliveryMock.getShipments()
      : httpGetList<ShipmentSummaryResponse>('/api/delivery/shipments');
  },

  /** GET /api/delivery/shipments/{deliveryId} — 내 배차 상세 */
  getShipment(deliveryId: number): Promise<ShipmentDetailResponse> {
    return config.useMock
      ? deliveryMock.getShipment(deliveryId)
      : httpGet<ShipmentDetailResponse>(`/api/delivery/shipments/${deliveryId}`);
  },

  /** POST /api/delivery/shipments/{deliveryId}/pickup — 가구 수령(응답 바디 없음) */
  async pickupShipment(deliveryId: number): Promise<void> {
    if (config.useMock) return deliveryMock.pickupShipment(deliveryId);
    await httpPost<void>(`/api/delivery/shipments/${deliveryId}/pickup`);
  },

  /** POST /api/delivery/shipments/{deliveryId}/completion — 배송 완료(응답 바디 없음) */
  async completeShipment(deliveryId: number): Promise<void> {
    if (config.useMock) return deliveryMock.completeShipment(deliveryId);
    await httpPost<void>(`/api/delivery/shipments/${deliveryId}/completion`);
  },
};
