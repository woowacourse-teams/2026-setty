import {
  DeliveryRequestDetailResponse,
  DeliveryRequestSummaryResponse,
  ShipmentDetailResponse,
  ShipmentSummaryResponse,
} from '@/model/delivery';
import { HttpError } from '@/lib/http';

/**
 * 실기기 구동 확인용 인메모리 목. (배차 API dev 베이스 URL이 아직 없음)
 * - 모든 데이터는 가상값이다(AGENTS 개인정보 규칙).
 * - category는 표시용 한글 문자열 그대로 둔다(백엔드도 한글로 내려올 예정).
 * - 요청(requests)과 내 배차(shipments)를 한 저장소로 두어, 수락하면 요청이
 *   내 배차(ACCEPTED)로 이동한다.
 */

interface MockRecord {
  deliveryId: number;
  orderId: number;
  itemName: string;
  category: string;
  pickupAddress: string;
  pickupPhone: string;
  destinationAddress: string;
  destinationPhone: string;
  deliveryFee: number;
  status: 'REQUESTED' | 'ACCEPTED' | 'PICKED_UP' | 'DELIVERED';
  requestedAt: string;
  acceptedAt: string | null;
  pickedUpAt: string | null;
  deliveredAt: string | null;
}

const REQUEST_SEED: MockRecord[] = [
  rec(20, 300, '책상 + 책장 세트', '책상', '서울 서초구 방배동', '서울 강동구 천호동', 39000, 'REQUESTED', '2026-08-26T01:20:00Z'),
  rec(21, 305, '원목 4인용 식탁', '식탁', '서울 성동구 성수동', '서울 마포구 합정동', 35000, 'REQUESTED', '2026-08-26T01:25:00Z'),
  rec(22, 311, '3인용 가죽 소파', '소파', '서울 강남구 청담동', '서울 성북구 정릉동', 52000, 'REQUESTED', '2026-08-26T01:28:00Z'),
  rec(23, 318, '2도어 냉장고', '기타 가구', '서울 은평구 응암동', '서울 서대문구 홍은동', 45000, 'REQUESTED', '2026-08-26T01:30:00Z'),
];

const SHIPMENT_SEED: MockRecord[] = [
  // 진행중
  {
    ...rec(8, 208, '3인용 패브릭 소파', '소파', '서울 강남구 논현동', '서울 송파구 잠실동', 42000, 'ACCEPTED', '2026-08-26T00:40:00Z'),
    acceptedAt: '2026-08-26T00:52:00Z',
  },
  {
    ...rec(1, 100, '원목 4인용 식탁', '식탁', '서울 성동구 성수동', '서울 마포구 합정동', 35000, 'PICKED_UP', '2026-08-25T22:00:00Z'),
    acceptedAt: '2026-08-25T22:10:00Z',
    pickedUpAt: '2026-08-25T23:00:00Z',
  },
  // 완료(정산 집계 대상)
  {
    ...rec(5, 161, '퀸 사이즈 침대 프레임', '침대', '서울 강서구 마곡동', '서울 양천구 목동', 48000, 'DELIVERED', '2026-08-26T00:00:00Z'),
    acceptedAt: '2026-08-26T00:05:00Z',
    pickedUpAt: '2026-08-26T00:40:00Z',
    deliveredAt: '2026-08-26T01:30:00Z',
  },
  {
    ...rec(4, 150, '6단 서랍 수납장', '수납장', '서울 관악구 봉천동', '서울 동작구 상도동', 29000, 'DELIVERED', '2026-08-25T23:00:00Z'),
    acceptedAt: '2026-08-25T23:08:00Z',
    pickedUpAt: '2026-08-25T23:40:00Z',
    deliveredAt: '2026-08-26T00:20:00Z',
  },
  {
    ...rec(2, 110, '1인용 리클라이너', '의자', '서울 용산구 이태원동', '서울 중구 신당동', 26000, 'DELIVERED', '2026-08-25T21:30:00Z'),
    acceptedAt: '2026-08-25T21:40:00Z',
    pickedUpAt: '2026-08-25T22:10:00Z',
    deliveredAt: '2026-08-25T23:00:00Z',
  },
];

function rec(
  deliveryId: number,
  orderId: number,
  itemName: string,
  category: string,
  pickupAddress: string,
  destinationAddress: string,
  deliveryFee: number,
  status: MockRecord['status'],
  requestedAt: string,
): MockRecord {
  return {
    deliveryId,
    orderId,
    itemName,
    category,
    pickupAddress,
    pickupPhone: '010-0000-0000',
    destinationAddress,
    destinationPhone: '010-0000-0000',
    deliveryFee,
    status,
    requestedAt,
    acceptedAt: null,
    pickedUpAt: null,
    deliveredAt: null,
  };
}

let requests: MockRecord[] = REQUEST_SEED.map((r) => ({ ...r }));
let shipments: MockRecord[] = SHIPMENT_SEED.map((r) => ({ ...r }));

const delay = (ms = 500) => new Promise((r) => setTimeout(r, ms));

function toRequestSummary(r: MockRecord): DeliveryRequestSummaryResponse {
  return {
    deliveryId: r.deliveryId,
    itemName: r.itemName,
    category: r.category,
    pickupAddress: r.pickupAddress,
    deliveryAddress: r.destinationAddress,
    deliveryFee: r.deliveryFee,
    status: r.status,
  };
}

function toRequestDetail(r: MockRecord): DeliveryRequestDetailResponse {
  return {
    deliveryId: r.deliveryId,
    orderId: r.orderId,
    furniture: { itemName: r.itemName, category: r.category },
    pickupAddress: r.pickupAddress,
    destinationAddress: r.destinationAddress,
    deliveryFee: r.deliveryFee,
    status: r.status,
    requestedAt: r.requestedAt,
  };
}

function toShipmentSummary(r: MockRecord): ShipmentSummaryResponse {
  return {
    deliveryId: r.deliveryId,
    itemName: r.itemName,
    category: r.category,
    pickupAddress: r.pickupAddress,
    deliveryAddress: r.destinationAddress,
    deliveryFee: r.deliveryFee,
    status: r.status,
  };
}

function toShipmentDetail(r: MockRecord): ShipmentDetailResponse {
  return {
    deliveryId: r.deliveryId,
    orderId: r.orderId,
    furniture: { itemName: r.itemName, category: r.category },
    pickup: { address: r.pickupAddress, phoneNumber: r.pickupPhone },
    destination: { address: r.destinationAddress, phoneNumber: r.destinationPhone },
    deliveryFee: r.deliveryFee,
    status: r.status,
    requestedAt: r.requestedAt,
    acceptedAt: r.acceptedAt,
    pickedUpAt: r.pickedUpAt,
    deliveredAt: r.deliveredAt,
  };
}

export const deliveryMock = {
  async getRequests(): Promise<DeliveryRequestSummaryResponse[]> {
    await delay();
    return requests.map(toRequestSummary);
  },

  async getRequest(deliveryId: number): Promise<DeliveryRequestDetailResponse> {
    await delay();
    const found = requests.find((r) => r.deliveryId === deliveryId);
    if (!found) throw new Error('요청을 찾을 수 없어요');
    return toRequestDetail(found);
  },

  async acceptRequest(deliveryId: number): Promise<void> {
    await delay();
    const found = requests.find((r) => r.deliveryId === deliveryId);
    if (!found) return;
    requests = requests.filter((r) => r.deliveryId !== deliveryId);
    // 수락하면 내 배차(ACCEPTED)로 이동한다.
    shipments = [{ ...found, status: 'ACCEPTED', acceptedAt: new Date().toISOString() }, ...shipments];
  },

  async getShipments(): Promise<ShipmentSummaryResponse[]> {
    await delay();
    return shipments.map(toShipmentSummary);
  },

  async getShipment(deliveryId: number): Promise<ShipmentDetailResponse> {
    await delay();
    const found = shipments.find((r) => r.deliveryId === deliveryId);
    if (!found) throw new HttpError(404, '배차를 찾을 수 없어요');
    return toShipmentDetail(found);
  },

  async pickupShipment(deliveryId: number): Promise<void> {
    await delay();
    const found = shipments.find((r) => r.deliveryId === deliveryId);
    // 가드: ACCEPTED일 때만 수령 가능(아니면 409).
    if (!found || found.status !== 'ACCEPTED') {
      throw new HttpError(409, '지금은 수령할 수 없는 상태예요');
    }
    found.status = 'PICKED_UP';
    found.pickedUpAt = new Date().toISOString();
  },

  async completeShipment(deliveryId: number): Promise<void> {
    await delay();
    const found = shipments.find((r) => r.deliveryId === deliveryId);
    // 가드: PICKED_UP일 때만 완료 가능(아니면 409).
    if (!found || found.status !== 'PICKED_UP') {
      throw new HttpError(409, '지금은 완료할 수 없는 상태예요');
    }
    found.status = 'DELIVERED';
    found.deliveredAt = new Date().toISOString();
  },

  /** 세션 초기화용(개발 편의). */
  reset(): void {
    requests = REQUEST_SEED.map((r) => ({ ...r }));
    shipments = SHIPMENT_SEED.map((r) => ({ ...r }));
  },
};
