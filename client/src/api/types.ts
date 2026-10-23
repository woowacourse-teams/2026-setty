// 서버 응답 형태. Android 앱 network 패키지의 응답 클래스와 같다.

export type AboutMeResponse = {
    address: string;
    id: number;
    loginId: string;
    phoneNumber: string;
    role: string;
    // 행정동을 고르기 전에 가입한 회원은 비어 있다
    dongCode?: string | null;
};

export type LoginResponse = {
    token: string;
    role: string;
};

export type SignUpRequest = {
    loginId: string;
    password: string;
    phoneNumber: string;
    address: string;
    dongCode: string;
};

export type DongResponse = {
    code: string;
    name: string;
    district: string;
};

export type ListingDongResponse = DongResponse;

export type FurnitureSummaryResponse = {
    id: number;
    title: string;
    thumbnailUrl?: string | null;
    price: number;
    deliveryFee: number;
    totalPrice: number;
    category: string;
    conditionGrade: string;
    createdAt: string;
    // 행정동 도입 전에 등록한 매물은 null
    dong?: ListingDongResponse | null;
    hasPurchaseRequest?: boolean;
};

export type ListingListResponse = {
    items: FurnitureSummaryResponse[];
    // 다음 페이지 커서. 마지막 페이지거나 페이지로 요청하지 않았으면 없다
    nextCursor?: string | null;
};

export type FurnitureDetailResponse = {
    id: number;
    title: string;
    description: string;
    price: number;
    deliveryFee: number;
    totalPrice: number;
    category: string;
    conditionGrade: string;
    dimensions: { widthCm: number; depthCm: number; heightCm: number };
    saleStatus: string;
    images: Array<{ id: number; url: string; displayOrder: number }>;
    createdAt: string;
    updatedAt: string;
    dong?: ListingDongResponse | null;
    hasPurchaseRequest?: boolean;
};

export type MyFurnitureResponse = {
    id: number;
    title: string;
    thumbnailUrl?: string | null;
    price: number;
    deliveryFee: number;
    totalPrice: number;
    category: string;
    conditionGrade: string;
    saleStatus: string;
    hasPurchaseRequest: boolean;
    canUpdate: boolean;
    canDelete: boolean;
    createdAt: string;
};

export type ListingUpsertRequest = {
    title: string;
    description: string;
    price: number;
    category: string;
    conditionGrade: string;
    dimensions: { widthCm: number; depthCm: number; heightCm: number };
    // 수정할 때 유지할 기존 이미지 id. 이 순서대로 재정렬되고 새 이미지는 뒤에 붙는다
    retainedImageIds?: number[];
};

export type ListingCreateResponse = {
    listingId: number;
    createdAt: string;
};

export type OrderCreateResponse = {
    id: number;
    listingId: number;
    buyerId: number;
    deliveryStatus: string;
    deliveryFee: number;
    // 매물 가격 + 주문에 저장한 배송비. 서버가 결제 승인 때 검증하는 금액이다
    totalPrice: number;
};

export type OrderResponse = {
    id: number;
    // 매물 레코드가 없으면 null
    listing?: {
        id: number;
        name: string;
        thumbnailUrl?: string | null;
        price: number;
        deliveryFee: number;
    } | null;
    // 서버가 내려주지 않는 버전도 있어 없으면 null
    orderStatus?: string | null;
    deliveryStatus: string;
};

export type OrderCancellationResponse = {
    orderId: number;
    orderStatus: string;
    message: string;
};
