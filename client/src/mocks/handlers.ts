import { delay, http, HttpResponse } from 'msw';
import { authHandlers } from './auth';
import { mockListings } from './listings';
import { getListingMockScenario } from './scenario';
import type { ListingDetail, ListingItem, ListingPayload, MyListing } from '../api/listings';
import type { MyOrder, Order } from '../api/orders';

let nextListingId = 3;
let nextImageId = 30;
let nextOrderId = 1;
const orderStore: Order[] = [];
let listingStore: ListingDetail[] = mockListings.map((listing, index) => ({
    ...listing,
    description: index === 0 ? '사용감이 적은 원목 가구입니다.' : '깨끗하게 관리한 가구입니다.',
    saleStatus: 'AVAILABLE',
    images: [{ id: index + 10, url: listing.thumbnailUrl ?? '', displayOrder: 1 }],
    updatedAt: listing.createdAt
}));

function isAuthenticated(request: Request) {
    return request.headers.get('Authorization')?.startsWith('Bearer ') ?? false;
}

function unauthorized() {
    return HttpResponse.json({ code: 'INVALID_TOKEN', message: '로그인이 필요합니다.' }, { status: 401 });
}

function summaryOf(listing: ListingDetail): ListingItem {
    const { description, saleStatus, images, updatedAt, ...summary } = listing;
    return { ...summary, thumbnailUrl: images[0]?.url ?? null };
}

function mineOf(listing: ListingDetail): MyListing {
    return {
        ...summaryOf(listing),
        saleStatus: listing.saleStatus,
        hasPurchaseRequest: false,
        canUpdate: listing.saleStatus === 'AVAILABLE',
        canDelete: listing.saleStatus === 'AVAILABLE'
    };
}

async function readPayload(request: Request): Promise<{ payload: ListingPayload & { retainedImageIds?: number[] }; imageCount: number }> {
    const formData = await request.formData();
    const rawRequest = formData.get('request');
    const text = rawRequest instanceof File ? await rawRequest.text() : rawRequest;
    return {
        payload: JSON.parse(String(text)) as ListingPayload & { retainedImageIds?: number[] },
        imageCount: formData.getAll('images').filter((image) => image instanceof File).length
    };
}

function invalidRequest(message: string) {
    return HttpResponse.json({ code: 'INVALID_REQUEST', message }, { status: 400 });
}

export const handlers = [
    ...authHandlers,
    http.get('/api/listings', async () => {
        if (getListingMockScenario() === 'slow') {
            await delay(1500);
        }

        return HttpResponse.json({
            items: listingStore.filter((listing) => listing.saleStatus === 'AVAILABLE').map(summaryOf)
        });
    }),

    http.get('/api/listings/:listingId', ({ params }) => {
        const listing = listingStore.find((item) => item.id === Number(params.listingId));
        return listing
            ? HttpResponse.json(listing)
            : HttpResponse.json({ code: 'LISTING_NOT_FOUND', message: '매물을 찾을 수 없습니다.' }, { status: 404 });
    }),

    http.get('/api/me/listings', ({ request }) => {
        if (!isAuthenticated(request)) return unauthorized();
        return HttpResponse.json({ items: listingStore.map(mineOf) });
    }),

    http.post('/api/listings', async ({ request }) => {
        if (!isAuthenticated(request)) return unauthorized();
        try {
            const { payload, imageCount } = await readPayload(request);
            if (!payload.title.trim() || !payload.description.trim() || imageCount < 1) return invalidRequest('필수 입력값을 확인해 주세요.');
            const createdAt = new Date().toISOString();
            const listingId = nextListingId++;
            const images = Array.from({ length: imageCount }, (_, index) => ({
                id: nextImageId++, url: '/images/listings/console-desk.png', displayOrder: index + 1
            }));
            listingStore = [{
                id: listingId,
                ...payload,
                deliveryFee: 10000,
                totalPrice: payload.price + 10000,
                saleStatus: 'AVAILABLE',
                images,
                createdAt,
                updatedAt: createdAt
            }, ...listingStore];
            return HttpResponse.json({ listingId, createdAt }, { status: 201, headers: { Location: `/api/listings/${listingId}` } });
        } catch {
            return invalidRequest('잘못된 등록 요청입니다.');
        }
    }),

    http.put('/api/listings/:listingId', async ({ params, request }) => {
        if (!isAuthenticated(request)) return unauthorized();
        const index = listingStore.findIndex((item) => item.id === Number(params.listingId));
        if (index < 0) return HttpResponse.json({ code: 'LISTING_NOT_FOUND', message: '매물을 찾을 수 없습니다.' }, { status: 404 });
        try {
            const { payload, imageCount } = await readPayload(request);
            const current = listingStore[index];
            const retained = (payload.retainedImageIds ?? []).map((id) => current.images.find((image) => image.id === id)).filter(Boolean) as ListingDetail['images'];
            const images = [...retained, ...Array.from({ length: imageCount }, (_, order) => ({ id: nextImageId++, url: '/images/listings/console-desk.png', displayOrder: retained.length + order + 1 }))];
            if (!payload.title.trim() || !payload.description.trim() || images.length < 1 || images.length > 5) return invalidRequest('필수 입력값을 확인해 주세요.');
            listingStore[index] = { ...current, ...payload, images: images.map((image, order) => ({ ...image, displayOrder: order + 1 })), deliveryFee: 10000, totalPrice: payload.price + 10000, updatedAt: new Date().toISOString() };
            return new HttpResponse(null, { status: 204 });
        } catch {
            return invalidRequest('잘못된 수정 요청입니다.');
        }
    }),

    http.delete('/api/listings/:listingId', ({ params, request }) => {
        if (!isAuthenticated(request)) return unauthorized();
        const index = listingStore.findIndex((item) => item.id === Number(params.listingId));
        if (index < 0) return HttpResponse.json({ code: 'LISTING_NOT_FOUND', message: '매물을 찾을 수 없습니다.' }, { status: 404 });
        listingStore = listingStore.filter((_, listingIndex) => listingIndex !== index);
        return new HttpResponse(null, { status: 204 });
    }),

    http.post('/api/orders', async ({ request }) => {
        if (!isAuthenticated(request)) return unauthorized();

        try {
            const body = await request.json() as { listingId?: unknown };
            const listingId = body.listingId;
            if (typeof listingId !== 'number') return invalidRequest('매물 정보를 확인해 주세요.');
            if (orderStore.some((order) => order.listingId === listingId)) {
                return HttpResponse.json({ code: 'ALREADY_ORDERED', message: '이미 주문한 매물입니다.' }, { status: 400 });
            }

            const order: Order = { id: nextOrderId++, listingId, buyerId: 1, deliveryStatus: 'REQUESTED' };
            orderStore.unshift(order);
            return HttpResponse.json(order, { status: 201 });
        } catch {
            return invalidRequest('잘못된 주문 요청입니다.');
        }
    }),

    http.get('/api/me/orders', ({ request }) => {
        if (!isAuthenticated(request)) return unauthorized();

        const orders: MyOrder[] = orderStore.map((order) => {
            const listing = listingStore.find((item) => item.id === order.listingId);
            return {
                id: order.id,
                listing: {
                    id: order.listingId,
                    name: listing?.title ?? '판매가 종료된 매물',
                    price: listing?.price ?? 0,
                    deliveryFee: listing?.deliveryFee ?? 0
                },
                deliveryStatus: order.deliveryStatus
            };
        });
        return HttpResponse.json(orders);
    })
];
