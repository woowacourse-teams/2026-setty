/**
 * server 배차 계약을 그대로 옮긴 타입이다. (명세: apps/docs/api-mapping.md)
 * 규칙:
 * - id / 금액: number (server Long)
 * - 타임스탬프: Instant(ISO 8601 문자열)로 받아 앱에서 파싱한다.
 * - 그 외 값: string. 필드명은 명세 예시 그대로 유지한다.
 * 계약에 없는 필드를 여기에서 추측해 추가하지 않는다.
 */

/** server가 Instant로 내려주는 ISO 8601 문자열. null 가능 필드는 각 타입에서 표기. */
export type Instant = string;

/**
 * server `DeliveryStatus`. 현재 4개이며 이후 확장될 수 있다.
 * (취소/거절/실패 등은 미확정 — 확정되면 여기에 추가한다.)
 */
export type DeliveryStatus = 'REQUESTED' | 'ACCEPTED' | 'PICKED_UP' | 'DELIVERED';

/**
 * 가구 정보. `category`는 표시용 문자열 그대로 렌더한다(코드→한글 매핑표를 만들지 않음).
 */
export interface FurnitureResponse {
  itemName: string;
  category: string;
}

/** 픽업/도착 지점. 전화번호는 배차 상세(내 배차)에서만 내려온다. */
export interface DeliveryPointResponse {
  address: string;
  phoneNumber: string;
}

/** GET /api/delivery/requests — 카드 1개 */
export interface DeliveryRequestSummaryResponse {
  deliveryId: number;
  itemName: string;
  category: string;
  pickupAddress: string;
  deliveryAddress: string;
  deliveryFee: number;
  status: DeliveryStatus;
}

/** GET /api/delivery/requests/{deliveryId} — 수신 상세(전화 없음, requestedAt만) */
export interface DeliveryRequestDetailResponse {
  deliveryId: number;
  orderId: number;
  furniture: FurnitureResponse;
  pickupAddress: string;
  /** 명세상 요청 상세는 `destinationAddress` 필드명을 쓴다(요약의 deliveryAddress와 다름). */
  destinationAddress: string;
  deliveryFee: number;
  status: DeliveryStatus;
  requestedAt: Instant;
}

/** 아래 두 타입은 '내 배차'(shipments) 계약이다. */

/** GET /api/delivery/shipments — 카드 1개 */
export interface ShipmentSummaryResponse {
  deliveryId: number;
  itemName: string;
  category: string;
  pickupAddress: string;
  deliveryAddress: string;
  deliveryFee: number;
  status: DeliveryStatus;
}

/** GET /api/delivery/shipments/{deliveryId} — 내 배차 상세(전화·타임스탬프 4종) */
export interface ShipmentDetailResponse {
  deliveryId: number;
  orderId: number;
  furniture: FurnitureResponse;
  pickup: DeliveryPointResponse;
  destination: DeliveryPointResponse;
  deliveryFee: number;
  status: DeliveryStatus;
  requestedAt: Instant;
  acceptedAt: Instant | null;
  pickedUpAt: Instant | null;
  deliveredAt: Instant | null;
}
