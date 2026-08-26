import {
  DeliveryRequestDetailResponse,
  DeliveryRequestSummaryResponse,
} from '@/model/delivery';

/**
 * 실기기 구동 확인용 인메모리 목. (배차 API dev 베이스 URL이 아직 없음)
 * - 모든 데이터는 가상값이다(AGENTS 개인정보 규칙).
 * - category는 표시용 한글 문자열 그대로 둔다(백엔드도 한글로 내려올 예정).
 * - 요청 상세에는 전화번호가 없다(명세). orderId/requestedAt만 채운다.
 */

interface MockRequest {
  deliveryId: number;
  orderId: number;
  itemName: string;
  category: string;
  pickupAddress: string;
  destinationAddress: string;
  deliveryFee: number;
  requestedAt: string;
}

const SEED: MockRequest[] = [
  {
    deliveryId: 20,
    orderId: 300,
    itemName: '책상 + 책장 세트',
    category: '책상',
    pickupAddress: '서울 서초구 방배동',
    destinationAddress: '서울 강동구 천호동',
    deliveryFee: 39000,
    requestedAt: '2026-08-26T01:20:00Z',
  },
  {
    deliveryId: 21,
    orderId: 305,
    itemName: '원목 4인용 식탁',
    category: '식탁',
    pickupAddress: '서울 성동구 성수동',
    destinationAddress: '서울 마포구 합정동',
    deliveryFee: 35000,
    requestedAt: '2026-08-26T01:25:00Z',
  },
  {
    deliveryId: 22,
    orderId: 311,
    itemName: '3인용 가죽 소파',
    category: '소파',
    pickupAddress: '서울 강남구 청담동',
    destinationAddress: '서울 성북구 정릉동',
    deliveryFee: 52000,
    requestedAt: '2026-08-26T01:28:00Z',
  },
  {
    deliveryId: 23,
    orderId: 318,
    itemName: '2도어 냉장고',
    category: '기타 가구',
    pickupAddress: '서울 은평구 응암동',
    destinationAddress: '서울 서대문구 홍은동',
    deliveryFee: 45000,
    requestedAt: '2026-08-26T01:30:00Z',
  },
];

// 세션 동안 유지되는 가변 목 상태(수락 시 제거).
let requests: MockRequest[] = [...SEED];

const delay = (ms = 500) => new Promise((r) => setTimeout(r, ms));

function toSummary(r: MockRequest): DeliveryRequestSummaryResponse {
  return {
    deliveryId: r.deliveryId,
    itemName: r.itemName,
    category: r.category,
    pickupAddress: r.pickupAddress,
    deliveryAddress: r.destinationAddress,
    deliveryFee: r.deliveryFee,
    status: 'REQUESTED',
  };
}

function toDetail(r: MockRequest): DeliveryRequestDetailResponse {
  return {
    deliveryId: r.deliveryId,
    orderId: r.orderId,
    furniture: { itemName: r.itemName, category: r.category },
    pickupAddress: r.pickupAddress,
    destinationAddress: r.destinationAddress,
    deliveryFee: r.deliveryFee,
    status: 'REQUESTED',
    requestedAt: r.requestedAt,
  };
}

export const deliveryMock = {
  async getRequests(): Promise<DeliveryRequestSummaryResponse[]> {
    await delay();
    return requests.map(toSummary);
  },

  async getRequest(deliveryId: number): Promise<DeliveryRequestDetailResponse> {
    await delay();
    const found = requests.find((r) => r.deliveryId === deliveryId);
    if (!found) throw new Error('요청을 찾을 수 없어요');
    return toDetail(found);
  },

  async acceptRequest(deliveryId: number): Promise<void> {
    await delay();
    requests = requests.filter((r) => r.deliveryId !== deliveryId);
  },

  /** 세션 초기화용(개발 편의). */
  reset(): void {
    requests = [...SEED];
  },
};
