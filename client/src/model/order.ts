export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCEL_PENDING' | 'CANCELLED' | 'EXPIRED';
export type DeliveryStatus = 'PENDING' | 'REQUESTED' | 'ACCEPTED' | 'PICKED_UP' | 'DELIVERED';

const orderStatuses: readonly OrderStatus[] = ['PENDING', 'CONFIRMED', 'CANCEL_PENDING', 'CANCELLED', 'EXPIRED'];
const deliveryStatuses: readonly DeliveryStatus[] = ['PENDING', 'REQUESTED', 'ACCEPTED', 'PICKED_UP', 'DELIVERED'];

export type OrderedFurniture = {
    id: number;
    name: string;
    thumbnailUrl: string | null;
    price: number;
    deliveryFee: number;
};

export type Order = {
    id: number;
    // 매물 레코드가 없으면 null
    listing: OrderedFurniture | null;
    // 서버가 주문 상태를 내려주지 않으면 null
    orderStatus: OrderStatus | null;
    deliveryStatus: DeliveryStatus;
};

/** 결제 전에 만든 PENDING 주문. [totalPrice]는 서버가 결제 승인 때 검증하는 금액이다. */
export type PendingOrder = {
    id: number;
    totalPrice: number;
};

export function orderedTotalPrice(listing: OrderedFurniture): number {
    return listing.price + listing.deliveryFee;
}

/** 결제가 끝난 주문만 취소를 요청할 수 있다. 주문 상태를 모르면 배송 요청 단계에서만 허용한다. */
export function canCancel(order: Order): boolean {
    if (order.orderStatus === null) return order.deliveryStatus === 'REQUESTED';
    return order.orderStatus === 'CONFIRMED';
}

/** 주문 상태 문자열을 변환한다. 알 수 없는 값이면 null. */
export function orderStatusOrNull(value: string | null | undefined): OrderStatus | null {
    return orderStatuses.find((status) => status === value) ?? null;
}

/** 알 수 없는 배송 상태는 결제 대기로 본다. */
export function deliveryStatusOf(value: string): DeliveryStatus {
    return deliveryStatuses.find((status) => status === value) ?? 'PENDING';
}

const orderStatusLabels: Record<OrderStatus, string> = {
    PENDING: '결제 대기',
    CONFIRMED: '주문 완료',
    CANCEL_PENDING: '취소 대기',
    CANCELLED: '주문 취소',
    EXPIRED: '결제 만료'
};

const deliveryStatusLabels: Record<DeliveryStatus, string> = {
    PENDING: '결제 대기',
    REQUESTED: '배송 요청',
    ACCEPTED: '배차 완료',
    PICKED_UP: '배송 중',
    DELIVERED: '배송 완료'
};

export function deliveryStatusLabel(status: DeliveryStatus): string {
    return deliveryStatusLabels[status];
}

/** 주문 목록·상세에서 대표로 보여줄 상태. 취소·만료 흐름이면 주문 상태를, 아니면 배송 상태를 보여준다. */
export function orderStatusLabel(order: Order): string {
    switch (order.orderStatus) {
        case 'CANCEL_PENDING':
        case 'CANCELLED':
        case 'EXPIRED':
        case 'PENDING':
            return orderStatusLabels[order.orderStatus];
        default:
            return deliveryStatusLabels[order.deliveryStatus];
    }
}

/** 결제가 끝나 배송원이 배차를 진행 중인 주문인지 여부. 이 동안은 화면을 주기적으로 다시 불러온다. */
export function isDeliveryInProgress(order: Order): boolean {
    if (order.orderStatus !== null && order.orderStatus !== 'CONFIRMED') return false;
    return order.deliveryStatus === 'REQUESTED' || order.deliveryStatus === 'ACCEPTED' || order.deliveryStatus === 'PICKED_UP';
}

/** 취소·만료처럼 배송 흐름에서 벗어난 주문인지 여부. */
export function isCancelFlow(order: Order): boolean {
    return order.orderStatus === 'CANCEL_PENDING'
        || order.orderStatus === 'CANCELLED'
        || order.orderStatus === 'EXPIRED';
}

export function orderStatusDescription(order: Order): string {
    switch (order.orderStatus) {
        case 'PENDING':
            return '결제가 완료되지 않은 주문이에요';
        case 'CANCEL_PENDING':
            return '취소 요청을 처리하고 있어요';
        case 'CANCELLED':
            return '주문이 취소됐어요';
        case 'EXPIRED':
            return '결제 시간이 지나 주문이 만료됐어요';
        default:
            switch (order.deliveryStatus) {
                case 'PENDING':
                    return '결제가 완료되지 않은 주문이에요';
                case 'REQUESTED':
                    return '배송 기사를 찾고 있어요';
                case 'ACCEPTED':
                    return '배송 기사가 배정됐어요';
                case 'PICKED_UP':
                    return '가구를 싣고 배송 중이에요';
                case 'DELIVERED':
                    return '배송이 완료됐어요';
            }
    }
}

/** 배송 요청부터 배송 완료까지의 단계. 진행 표시에 쓴다. */
export const deliverySteps: readonly DeliveryStatus[] = ['REQUESTED', 'ACCEPTED', 'PICKED_UP', 'DELIVERED'];

/** 배송 진행률. 결제 전이면 0, 배송 완료면 1. */
export function deliveryProgress(status: DeliveryStatus): number {
    return (deliverySteps.indexOf(status) + 1) / deliverySteps.length;
}
