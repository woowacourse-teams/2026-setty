export const listingCategories = ['SOFA', 'TABLE', 'DESK', 'CHAIR', 'STORAGE', 'BED'] as const;
export const conditionGrades = ['S', 'A', 'B', 'C'] as const;

export type ListingCategory = (typeof listingCategories)[number];
export type ConditionGrade = (typeof conditionGrades)[number];
export type Dimensions = {
    widthCm: number;
    depthCm: number;
    heightCm: number;
};

export type ListingItem = {
    id: number;
    title: string;
    thumbnailUrl: string | null;
    price: number;
    deliveryFee: number;
    totalPrice: number;
    category: ListingCategory;
    conditionGrade: ConditionGrade;
    dimensions: Dimensions;
    createdAt: string;
};

export type ListingDetail = Omit<ListingItem, 'thumbnailUrl'> & {
    description: string;
    saleStatus: 'AVAILABLE' | 'RESERVED' | 'SOLD';
    images: Array<{ id: number; url: string; displayOrder: number }>;
    updatedAt: string;
};

export type MyListing = ListingItem & {
    saleStatus: 'AVAILABLE' | 'RESERVED' | 'SOLD';
    hasPurchaseRequest: boolean;
    canUpdate: boolean;
    canDelete: boolean;
};

export type ListingPayload = {
    title: string;
    description: string;
    price: number;
    category: ListingCategory;
    conditionGrade: ConditionGrade;
    dimensions: Dimensions;
};

type ListingListResponse<T> = { items: T[] };

export class ListingApiError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'ListingApiError';
    }
}

function authorizationHeaders(): Record<string, string> {
    const token = window.sessionStorage.getItem('setty:auth-token');
    return token ? { Authorization: `Bearer ${token}` } : {};
}

async function ensureSuccess(response: Response, fallbackMessage: string) {
    if (response.ok) return;

    let message = fallbackMessage;
    try {
        const body = await response.json() as { message?: string };
        message = body.message ?? fallbackMessage;
    } catch {
        // The API can return an empty error response.
    }
    throw new ListingApiError(message);
}

function listingFormData(payload: ListingPayload & { retainedImageIds?: number[] }, images: File[]) {
    const body = new FormData();
    body.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    images.forEach((image) => body.append('images', image));
    return body;
}

export async function fetchListings(): Promise<ListingItem[]> {
    const response = await fetch('/api/listings');
    await ensureSuccess(response, '매물 목록을 불러오지 못했습니다.');
    return (await response.json() as ListingListResponse<ListingItem>).items;
}

export async function fetchListing(listingId: number): Promise<ListingDetail> {
    const response = await fetch(`/api/listings/${listingId}`);
    await ensureSuccess(response, '매물 상세를 불러오지 못했습니다.');
    return response.json() as Promise<ListingDetail>;
}

export async function fetchMyListings(): Promise<MyListing[]> {
    const response = await fetch('/api/me/listings', { headers: authorizationHeaders() });
    await ensureSuccess(response, '내 가구 목록을 불러오지 못했습니다. 로그인 상태를 확인해 주세요.');
    return (await response.json() as ListingListResponse<MyListing>).items;
}

export async function createListing(payload: ListingPayload, images: File[]) {
    const response = await fetch('/api/listings', {
        method: 'POST',
        headers: authorizationHeaders(),
        body: listingFormData(payload, images)
    });
    await ensureSuccess(response, '가구를 등록하지 못했습니다.');
    return response.json() as Promise<{ listingId: number; createdAt: string }>;
}

export async function updateListing(listingId: number, payload: ListingPayload, retainedImageIds: number[], images: File[]) {
    const response = await fetch(`/api/listings/${listingId}`, {
        method: 'PUT',
        headers: authorizationHeaders(),
        body: listingFormData({ ...payload, retainedImageIds }, images)
    });
    await ensureSuccess(response, '가구 정보를 수정하지 못했습니다.');
}

export async function deleteListing(listingId: number) {
    const response = await fetch(`/api/listings/${listingId}`, {
        method: 'DELETE',
        headers: authorizationHeaders()
    });
    await ensureSuccess(response, '가구를 내리지 못했습니다.');
}
