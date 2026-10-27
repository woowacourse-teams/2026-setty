import { dongDisplayName, type Dong } from '../model/dong';
import {
    furnitureCategoryOf,
    saleStatusOf,
    type EditableListing,
    type FurnitureDetail,
    type FurniturePage,
    type FurnitureSummary,
    type ListingDraft,
    type MyFurniture,
    orderStateOf
} from '../model/furniture';
import type { Member } from '../model/member';
import { deliveryStatusOf, orderStatusOrNull, type Order, type OrderStatus, type PendingOrder } from '../model/order';
import { apiCall, tokenStore } from './http';
import type {
    AboutMeResponse,
    DongResponse,
    FurnitureDetailResponse,
    FurnitureSummaryResponse,
    ListingCreateResponse,
    ListingListResponse,
    ListingUpsertRequest,
    LoginResponse,
    MyFurnitureResponse,
    OrderCancellationResponse,
    OrderCreateResponse,
    OrderResponse,
    SignUpRequest
} from './types';

const PAGE_SIZE = 20;
// 서버는 한 번에 올리는 이미지 전체 용량을 25MB로 제한한다
const MAX_TOTAL_IMAGE_BYTES = 25 * 1024 * 1024;

export class ListingImageTooLargeError extends Error {
    constructor() {
        super('사진 전체 용량은 25MB 이하여야 합니다');
        this.name = 'ListingImageTooLargeError';
    }
}

function toMember(response: AboutMeResponse): Member {
    return {
        id: response.id,
        loginId: response.loginId,
        phoneNumber: response.phoneNumber,
        address: response.address,
        dongCode: response.dongCode ?? null
    };
}

export const userRepository = {
    async login(loginId: string, password: string) {
        const result = await apiCall<LoginResponse>('/api/auth/login', { method: 'POST', body: { loginId, password } });
        tokenStore.set(result.token);
    },

    async signUp(request: SignUpRequest) {
        await apiCall('/api/auth/signup', { method: 'POST', body: request });
    },

    async getMe(): Promise<Member> {
        return toMember(await apiCall<AboutMeResponse>('/api/auth/me'));
    },

    async updateMe(phoneNumber: string, address: string): Promise<Member> {
        return toMember(await apiCall<AboutMeResponse>('/api/auth/me', { method: 'PUT', body: { phoneNumber, address } }));
    },

    async updateDong(dongCode: string): Promise<Member> {
        return toMember(await apiCall<AboutMeResponse>('/api/auth/me/dong', { method: 'PUT', body: { dongCode } }));
    },

    /** 서버 응답과 상관없이 저장한 토큰을 지운다. */
    async logout() {
        try {
            await apiCall('/api/auth/logout', { method: 'POST' });
        } catch {
            // 서버에 알리지 못했거나 이미 만료된 토큰이어도 브라우저에서는 로그아웃한다
        } finally {
            tokenStore.clear();
        }
    }
};

let cachedDongs: Promise<Dong[]> | null = null;

export const dongRepository = {
    /** 서울 행정동 목록은 거의 바뀌지 않으므로 페이지가 떠 있는 동안 한 번만 불러온다. */
    getDongs(): Promise<Dong[]> {
        if (!cachedDongs) {
            cachedDongs = apiCall<{ items: DongResponse[] }>('/api/dongs')
                .then((response) => response.items.map(({ code, name, district }) => ({ code, name, district })));
            cachedDongs.catch(() => {
                cachedDongs = null;
            });
        }
        return cachedDongs;
    }
};

function toFurniture(response: FurnitureSummaryResponse): FurnitureSummary {
    return {
        id: response.id,
        title: response.title,
        thumbnailUrl: response.thumbnailUrl ?? null,
        price: response.price,
        deliveryFee: response.deliveryFee,
        totalPrice: response.totalPrice,
        category: furnitureCategoryOf(response.category),
        conditionGrade: response.conditionGrade,
        createdAt: response.createdAt,
        dongName: response.dong ? dongDisplayName(response.dong) : null,
        hasPurchaseRequest: response.hasPurchaseRequest ?? false,
        orderState: orderStateOf(response.orderState, response.hasPurchaseRequest ?? false)
    };
}

function sortedImages(response: FurnitureDetailResponse) {
    return [...response.images].sort((left, right) => left.displayOrder - right.displayOrder);
}

function toFurnitureDetail(response: FurnitureDetailResponse): FurnitureDetail {
    return {
        id: response.id,
        title: response.title,
        description: response.description,
        price: response.price,
        deliveryFee: response.deliveryFee,
        totalPrice: response.totalPrice,
        category: furnitureCategoryOf(response.category),
        conditionGrade: response.conditionGrade,
        widthCm: response.dimensions.widthCm,
        depthCm: response.dimensions.depthCm,
        heightCm: response.dimensions.heightCm,
        saleStatus: saleStatusOf(response.saleStatus),
        imageUrls: sortedImages(response).map((image) => image.url),
        dongName: response.dong ? dongDisplayName(response.dong) : null,
        hasPurchaseRequest: response.hasPurchaseRequest ?? false,
        purchaseRequestedByMe: response.purchaseRequestedByMe ?? false,
        orderState: orderStateOf(response.orderState, response.hasPurchaseRequest ?? false),
        myOrderId: response.myOrderId ?? null
    };
}

function toEditableListing(response: FurnitureDetailResponse): EditableListing {
    return {
        id: response.id,
        draft: {
            title: response.title,
            description: response.description,
            price: response.price,
            category: furnitureCategoryOf(response.category),
            conditionGrade: response.conditionGrade,
            widthCm: response.dimensions.widthCm,
            depthCm: response.dimensions.depthCm,
            heightCm: response.dimensions.heightCm
        },
        images: sortedImages(response).map((image) => ({ id: image.id, url: image.url }))
    };
}

function toMyFurniture(response: MyFurnitureResponse): MyFurniture {
    return {
        id: response.id,
        title: response.title,
        thumbnailUrl: response.thumbnailUrl ?? null,
        price: response.price,
        deliveryFee: response.deliveryFee,
        totalPrice: response.totalPrice,
        category: furnitureCategoryOf(response.category),
        conditionGrade: response.conditionGrade,
        saleStatus: saleStatusOf(response.saleStatus),
        hasPurchaseRequest: response.hasPurchaseRequest,
        canUpdate: response.canUpdate,
        canDelete: response.canDelete,
        createdAt: response.createdAt
    };
}

function listingFormData(request: ListingUpsertRequest, images: File[]): FormData {
    if (images.reduce((total, image) => total + image.size, 0) > MAX_TOTAL_IMAGE_BYTES) {
        throw new ListingImageTooLargeError();
    }
    const body = new FormData();
    body.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    images.forEach((image) => body.append('images', image));
    return body;
}

function toUpsertRequest(draft: ListingDraft): ListingUpsertRequest {
    return {
        title: draft.title,
        description: draft.description,
        price: draft.price,
        category: draft.category,
        conditionGrade: draft.conditionGrade,
        dimensions: { widthCm: draft.widthCm, depthCm: draft.depthCm, heightCm: draft.heightCm }
    };
}

export const furnitureRepository = {
    async getFurniture(): Promise<FurnitureSummary[]> {
        return (await apiCall<ListingListResponse>('/api/listings')).items.map(toFurniture);
    },

    /** 배송비가 [maxDeliveryFee] 이하인 매물 한 페이지를 받는다. 첫 페이지는 [cursor]를 null로 넘긴다. */
    async getFurnitureWithinDeliveryFee(maxDeliveryFee: number, cursor: string | null): Promise<FurniturePage> {
        const response = await apiCall<ListingListResponse>('/api/listings', {
            query: { maxDeliveryFee, size: PAGE_SIZE, cursor }
        });
        return { items: response.items.map(toFurniture), nextCursor: response.nextCursor ?? null };
    },

    async getFurnitureDetail(listingId: number): Promise<FurnitureDetail> {
        return toFurnitureDetail(await apiCall<FurnitureDetailResponse>(`/api/listings/${listingId}`));
    },

    async getMyFurniture(): Promise<MyFurniture[]> {
        return (await apiCall<{ items: MyFurnitureResponse[] }>('/api/me/listings')).items.map(toMyFurniture);
    },

    async getEditableListing(listingId: number): Promise<EditableListing> {
        return toEditableListing(await apiCall<FurnitureDetailResponse>(`/api/listings/${listingId}`));
    },

    /** 매물을 등록하고 등록된 매물 id를 돌려준다. */
    async createListing(draft: ListingDraft, images: File[]): Promise<number> {
        const body = listingFormData(toUpsertRequest(draft), images);
        return (await apiCall<ListingCreateResponse>('/api/listings', { method: 'POST', body })).listingId;
    },

    /** 기존 이미지는 [retainedImageIds] 순서로 유지하고 [newImages]를 뒤에 추가한다. */
    async updateListing(listingId: number, draft: ListingDraft, retainedImageIds: number[], newImages: File[]) {
        const body = listingFormData({ ...toUpsertRequest(draft), retainedImageIds }, newImages);
        await apiCall(`/api/listings/${listingId}`, { method: 'PUT', body });
    },

    async deleteListing(listingId: number) {
        await apiCall(`/api/listings/${listingId}`, { method: 'DELETE' });
    }
};

export const favoriteRepository = {
    async getFavorites(): Promise<FurnitureSummary[]> {
        return (await apiCall<ListingListResponse>('/api/me/favorites')).items.map(toFurniture);
    },

    async isFavorited(listingId: number): Promise<boolean> {
        return (await apiCall<{ favorited: boolean }>(`/api/me/favorites/${listingId}`)).favorited;
    },

    async add(listingId: number) {
        await apiCall(`/api/me/favorites/${listingId}`, { method: 'PUT' });
    },

    async remove(listingId: number) {
        await apiCall(`/api/me/favorites/${listingId}`, { method: 'DELETE' });
    }
};

function toOrder(response: OrderResponse): Order {
    return {
        id: response.id,
        listing: response.listing
            ? {
                id: response.listing.id,
                name: response.listing.name,
                thumbnailUrl: response.listing.thumbnailUrl ?? null,
                price: response.listing.price,
                deliveryFee: response.listing.deliveryFee
            }
            : null,
        orderStatus: orderStatusOrNull(response.orderStatus),
        deliveryStatus: deliveryStatusOf(response.deliveryStatus)
    };
}

export const orderRepository = {
    async getMyOrders(): Promise<Order[]> {
        return (await apiCall<OrderResponse[]>('/api/me/orders')).map(toOrder);
    },

    async getOrder(orderId: number): Promise<Order> {
        return toOrder(await apiCall<OrderResponse>(`/api/orders/${orderId}`));
    },

    /** 취소 요청을 접수한다. 접수 시점의 주문 상태를 돌려주며 최종 결과는 주문 상세에서 확인한다. */
    async cancel(orderId: number): Promise<OrderStatus | null> {
        const response = await apiCall<OrderCancellationResponse>(`/api/orders/${orderId}/cancellations`, { method: 'POST' });
        return orderStatusOrNull(response.orderStatus);
    }
};

export const paymentRepository = {
    /** 결제 전에 PENDING 주문을 만들고 서버 내부 주문 id와 결제할 총액을 돌려준다. */
    async createOrder(listingId: number): Promise<PendingOrder> {
        const response = await apiCall<OrderCreateResponse>('/api/orders', { method: 'POST', body: { listingId } });
        return { id: response.id, totalPrice: response.totalPrice };
    },

    /** 토스 결제 성공 결과로 서버 승인을 요청한다. 이미 승인된 주문이면 서버가 기존 결과를 돌려준다(멱등). */
    async confirm(paymentKey: string, tossOrderId: string, amount: number): Promise<{ orderId: number; amount: number }> {
        const response = await apiCall<{ orderId: number; status: string; amount: number }>(
            '/api/payments/confirm',
            { method: 'POST', body: { paymentKey, orderId: tossOrderId, amount } }
        );
        return { orderId: response.orderId, amount: response.amount };
    },

    /** 결제 실패·취소를 알려 PENDING 주문을 정리(매물 선점 해제)한다. */
    async fail(tossOrderId: string) {
        await apiCall('/api/payments/fail', { method: 'POST', body: { orderId: tossOrderId } });
    }
};
