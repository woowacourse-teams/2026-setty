import { categoryLabel, furnitureCategories, type FurnitureCategory } from './furniture';

// 배송원 웹의 배차 계약. 서버 `/api/delivery/**` 응답을 그대로 옮긴다.

export type DeliveryStatus = 'REQUESTED' | 'ACCEPTED' | 'PICKED_UP' | 'DELIVERED';

export type ShipmentGroup = 'IN_PROGRESS' | 'DELIVERED';

export type DeliveryPage = {
    items: DeliverySummary[];
    nextCursor: string | null;
};

/** 배차 요청 목록과 내 배차 목록의 카드 하나. */
export type DeliverySummary = {
    deliveryId: number;
    itemName: string;
    category: string;
    thumbnailUrl: string | null;
    pickupAddress: string;
    deliveryAddress: string;
    deliveryFee: number;
    estimatedDistanceMeters: number | null;
    status: DeliveryStatus;
};

/** 지금 칸에서 출발해 내 진행 중 배차의 도착 칸 근처로 가는 대기 요청. 서버가 내 칸을 모르면 cell이 null이다. */
export type BundleCandidates = {
    cell: number | null;
    items: DeliverySummary[];
};

/** 아직 수락하지 않은 배차 요청 상세. 전화번호는 수락한 뒤에만 볼 수 있다. */
export type DeliveryRequestDetail = {
    deliveryId: number;
    orderId: number;
    furniture: { itemName: string; category: string; thumbnailUrl: string | null };
    pickupAddress: string;
    destinationAddress: string;
    deliveryFee: number;
    estimatedDistanceMeters: number | null;
    status: DeliveryStatus;
    requestedAt: string;
};

export type DeliveryPoint = {
    address: string;
    phoneNumber: string;
};

/** 내가 수락한 배차 상세. */
export type ShipmentDetail = {
    deliveryId: number;
    orderId: number;
    furniture: { itemName: string; category: string; thumbnailUrl: string | null };
    pickup: DeliveryPoint;
    destination: DeliveryPoint;
    deliveryFee: number;
    estimatedDistanceMeters: number | null;
    status: DeliveryStatus;
    requestedAt: string;
    acceptedAt: string | null;
    pickedUpAt: string | null;
    deliveredAt: string | null;
};

export type DeliverySignUpForm = {
    loginId: string;
    password: string;
    phoneNumber: string;
    licensePlateNumber: string;
    carType: string;
    businessRegistrationNumber: string;
};

export const LICENSE_PLATE_REGEX = /^\d{2,3}[가-힣]\d{4}$/;
export const BUSINESS_NUMBER_REGEX = /^\d{3}-\d{2}-\d{5}$/;
export const CAR_TYPE_MAX_LENGTH = 30;

/** 숫자만 입력해도 000-00-00000 형식으로 맞춘다. */
export function formatBusinessNumber(input: string): string {
    const digits = input.replace(/\D/g, '').slice(0, 10);
    if (digits.length <= 3) return digits;
    if (digits.length <= 5) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
    return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}`;
}

const statusLabels: Record<DeliveryStatus, string> = {
    REQUESTED: '배차 요청',
    ACCEPTED: '픽업 대기',
    PICKED_UP: '배송 중',
    DELIVERED: '배송 완료'
};

export function shipmentStatusLabel(status: DeliveryStatus): string {
    return statusLabels[status];
}

/** 수락부터 배송 완료까지의 단계. 진행 표시에 쓴다. */
export const shipmentSteps: readonly DeliveryStatus[] = ['ACCEPTED', 'PICKED_UP', 'DELIVERED'];

export function shipmentProgress(status: DeliveryStatus): number {
    return (shipmentSteps.indexOf(status) + 1) / shipmentSteps.length;
}

/** "서울 성동구 성수동 123" → "성수동 123"처럼 구 다음부터 보여준다. 짧은 주소는 그대로 둔다. */
export function shortAddress(address: string): string {
    const parts = address.trim().split(/\s+/);
    return parts.length > 2 ? parts.slice(2).join(' ') : address;
}

/** 서버 시각(ISO 8601)을 한국 시각 HH:mm으로 보여준다. */
export function formatTime(iso: string | null): string {
    if (!iso) return '';
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return '';
    return new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Seoul' }).format(date);
}

/** 판매자 수령지에서 구매자 목적지까지의 행정동 대표점 기준 추정 거리. */
export function formatEstimatedDistance(meters: number | null | undefined): string {
    if (meters == null) return '거리 정보 없음';
    const kilometers = (meters / 1000).toLocaleString('ko-KR', { maximumFractionDigits: 1 });
    return `예상 거리 ${kilometers}km (지역 기준)`;
}

/** 서버가 카테고리 코드(SOFA 등)를 내려주면 한글로 바꾸고, 이미 표시용 문자열이면 그대로 보여준다. */
export function categoryText(value: string): string {
    return (furnitureCategories as readonly string[]).includes(value) ? categoryLabel(value as FurnitureCategory) : value;
}
