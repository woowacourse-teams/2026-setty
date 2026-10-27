export const furnitureCategories = ['SOFA', 'TABLE', 'DESK', 'CHAIR', 'STORAGE', 'BED', 'ETC'] as const;
export type FurnitureCategory = (typeof furnitureCategories)[number];

// 서버가 받는 카테고리. 기타는 고를 수 없다
export const selectableCategories = furnitureCategories.filter((category) => category !== 'ETC');

/** 서버가 받는 상품 상태 등급. 좋은 등급부터 나열한다. */
export const conditionGrades = ['S', 'A', 'B', 'C'] as const;

export type SaleStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD';

const categoryLabels: Record<FurnitureCategory, string> = {
    SOFA: '소파',
    TABLE: '테이블',
    DESK: '책상',
    CHAIR: '의자',
    STORAGE: '수납장',
    BED: '침대',
    ETC: '기타'
};

export function categoryLabel(category: FurnitureCategory): string {
    return categoryLabels[category];
}

/** 알 수 없는 카테고리는 기타로 변환한다. */
export function furnitureCategoryOf(value: string): FurnitureCategory {
    return (furnitureCategories as readonly string[]).includes(value) ? value as FurnitureCategory : 'ETC';
}

/** 매물에 걸린 주문 단계. 결제 대기 주문이 선점 중이면 PAYING, 결제가 끝나 배송 완료 전이면 TRADING */
export type OrderState = 'NONE' | 'PAYING' | 'TRADING';

/** 알 수 없는 주문 단계는 주문 없음으로 본다. */
export function orderStateOf(value: string | undefined, hasPurchaseRequest: boolean): OrderState {
    if (value === 'PAYING' || value === 'TRADING') return value;
    if (value === 'NONE') return 'NONE';
    return hasPurchaseRequest ? 'PAYING' : 'NONE';
}

const saleStatusLabels: Record<SaleStatus, string> = {
    AVAILABLE: '판매중',
    RESERVED: '거래중',
    SOLD: '판매완료'
};

export function saleStatusLabel(status: SaleStatus): string {
    return saleStatusLabels[status];
}

/** 알 수 없는 판매 상태는 판매완료로 본다. */
export function saleStatusOf(value: string): SaleStatus {
    return value === 'AVAILABLE' || value === 'RESERVED' || value === 'SOLD' ? value : 'SOLD';
}

export const PAYING_LABEL = '결제 중';
export const TRADING_LABEL = '거래중';

/** 사용자에게 보여 주는 매물 상태. 기사 배차 여부(RESERVED)는 구매자에게 의미가 없어 거래중으로 묶는다. */
export function tradeStatusLabel(saleStatus: SaleStatus, orderState: OrderState): string {
    if (saleStatus === 'SOLD') return saleStatusLabel('SOLD');
    if (saleStatus === 'RESERVED' || orderState === 'TRADING') return TRADING_LABEL;
    if (orderState === 'PAYING') return PAYING_LABEL;
    return saleStatusLabel('AVAILABLE');
}

export type FurnitureSummary = {
    id: number;
    title: string;
    thumbnailUrl: string | null;
    price: number;
    deliveryFee: number;
    totalPrice: number;
    category: FurnitureCategory;
    conditionGrade: string;
    createdAt: string;
    // 판매자 행정동 표시 이름(예: "마포구 서교동"). 행정동 도입 전에 등록한 매물은 null
    dongName: string | null;
    // 결제 대기 주문이 매물을 선점하고 있으면 true. 선점은 판매 상태를 바꾸지 않는다
    hasPurchaseRequest: boolean;
    orderState: OrderState;
};

export type FurnitureDetail = {
    id: number;
    title: string;
    description: string;
    price: number;
    deliveryFee: number;
    totalPrice: number;
    category: FurnitureCategory;
    conditionGrade: string;
    widthCm: number;
    depthCm: number;
    heightCm: number;
    saleStatus: SaleStatus;
    imageUrls: string[];
    dongName: string | null;
    hasPurchaseRequest: boolean;
    // 선점한 결제 대기 주문이 내 것인지. 결제창을 닫고 돌아온 구매자가 결제를 이어서 할 수 있다
    purchaseRequestedByMe: boolean;
    orderState: OrderState;
    // 내가 이 매물에 가진 진행 중 주문 id. 결제가 끝난 구매자를 주문 상세로 보낸다
    myOrderId: number | null;
};

/** 판매중이지만 결제 대기 주문이 선점한 매물인지. 판매중이 아니면 판매 상태를 우선한다. */
export function isPaymentInProgress(listing: FurnitureDetail): boolean {
    return listing.saleStatus === 'AVAILABLE' && listing.orderState === 'PAYING';
}

/** 결제가 끝나 배송 완료 전인 매물인지. 기사 배차 전후를 가리지 않는다. */
export function isTrading(listing: FurnitureDetail): boolean {
    return listing.saleStatus === 'RESERVED' || (listing.saleStatus === 'AVAILABLE' && listing.orderState === 'TRADING');
}

/** 내 결제 대기 주문이 선점한 매물인지. 새 주문 없이 결제 화면으로 다시 들어갈 수 있다. */
export function canResumePayment(listing: FurnitureDetail): boolean {
    return isPaymentInProgress(listing) && listing.purchaseRequestedByMe;
}

/** 내가 결제를 끝낸 매물인지. 상세에서 내 주문으로 바로 갈 수 있다. */
export function hasMyOrder(listing: FurnitureDetail): boolean {
    return listing.myOrderId !== null && !canResumePayment(listing);
}

export function isPurchasable(listing: FurnitureDetail): boolean {
    return listing.saleStatus === 'AVAILABLE' && !listing.hasPurchaseRequest && listing.orderState === 'NONE';
}

/** 내가 등록한 매물. 판매 상태와 수정·삭제 가능 여부를 함께 가진다. */
export type MyFurniture = {
    id: number;
    title: string;
    thumbnailUrl: string | null;
    price: number;
    deliveryFee: number;
    totalPrice: number;
    category: FurnitureCategory;
    conditionGrade: string;
    saleStatus: SaleStatus;
    hasPurchaseRequest: boolean;
    canUpdate: boolean;
    canDelete: boolean;
    createdAt: string;
};

/** 매물 등록·수정 화면에서 서버로 보낼 매물 정보. */
export type ListingDraft = {
    title: string;
    description: string;
    price: number;
    category: FurnitureCategory;
    conditionGrade: string;
    widthCm: number;
    depthCm: number;
    heightCm: number;
};

export type ListingImage = {
    id: number;
    url: string;
};

/** 수정 화면에서 쓰는 기존 매물. 이미지를 유지·삭제하려면 이미지 id가 필요하다. */
export type EditableListing = {
    id: number;
    draft: ListingDraft;
    images: ListingImage[];
};

/** 커서로 나눠 받은 매물 목록. [nextCursor]가 null이면 마지막 페이지다. */
export type FurniturePage = {
    items: FurnitureSummary[];
    nextCursor: string | null;
};

const wonFormat = new Intl.NumberFormat('ko-KR');

export function formatWon(value: number): string {
    return `${wonFormat.format(value)}원`;
}

/** ISO-8601 문자열(예: 2026-09-30T05:03:41Z)에서 "9.30" 형태의 등록일을 만든다. */
export function formatRegisteredDate(createdAt: string): string {
    if (createdAt.length < 10) return '';
    const month = Number.parseInt(createdAt.substring(5, 7), 10);
    const day = Number.parseInt(createdAt.substring(8, 10), 10);
    if (Number.isNaN(month) || Number.isNaN(day)) return '';
    return `${month}.${day}`;
}
