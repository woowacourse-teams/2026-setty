import { delay, http, HttpResponse } from 'msw';
import {
    deliveryFeeOf,
    dongs,
    favorites,
    keywords,
    listings,
    members,
    newImageId,
    newKeywordId,
    notifications,
    orders,
    type MockKeyword,
    type MockListing,
    type MockMember,
    type MockOrder
} from './data';

const MAX_KEYWORDS = 10;
const MAX_EXCLUDE_KEYWORDS = 10;
const CONDITION_AMOUNT_MAX = 100_000_000;
const KEYWORD_CATEGORIES = ['SOFA', 'TABLE', 'DESK', 'CHAIR', 'STORAGE', 'BED'];

type ConditionsBody = {
    category?: string | null;
    minPrice?: number | null;
    maxPrice?: number | null;
    maxDeliveryFee?: number | null;
    maxTotalPrice?: number | null;
    excludeKeywords?: string[] | null;
};

function invalidAmount(value: number | null | undefined) {
    return value !== null && value !== undefined && (value < 0 || value > CONDITION_AMOUNT_MAX);
}

/** 서버 `KeywordConditions` 검증과 같은 규칙. 어긋나면 에러 문구를 돌려준다. */
function conditionsError(keyword: MockKeyword, body: ConditionsBody): string | null {
    const category = body.category?.trim().toUpperCase() || null;
    if (category && !KEYWORD_CATEGORIES.includes(category)) return '카테고리가 올바르지 않습니다';
    if ([body.minPrice, body.maxPrice, body.maxDeliveryFee, body.maxTotalPrice].some(invalidAmount)) return '금액은 0원 이상 1억 원 이하여야 합니다';
    if (body.minPrice != null && body.maxPrice != null && body.minPrice > body.maxPrice) return '최소 가격은 최대 가격보다 클 수 없습니다';
    const excludes = normalizedExcludes(body);
    if (excludes.length > MAX_EXCLUDE_KEYWORDS) return `제외 키워드는 ${MAX_EXCLUDE_KEYWORDS}개까지 걸 수 있습니다`;
    if (excludes.some((word) => word.length > 20)) return '제외 키워드는 20자 이하여야 합니다';
    if (excludes.some((word) => word.toLowerCase() === keyword.keyword.toLowerCase())) return '제외 키워드는 키워드와 같을 수 없습니다';
    return null;
}

function normalizedExcludes(body: ConditionsBody): string[] {
    const words: string[] = [];
    (body.excludeKeywords ?? []).forEach((word) => {
        const stripped = word.trim();
        if (stripped && !words.some((item) => item.toLowerCase() === stripped.toLowerCase())) words.push(stripped);
    });
    return words;
}

function keywordOf(keyword: MockKeyword) {
    const { memberId: _memberId, ...response } = keyword;
    return response;
}

/** 매물 부피 배송비에 받는 사람의 동이 판매자 동과 다르면 5천 원을 얹어 거리 배송비를 흉내낸다. */
function deliveryFeeFor(listing: MockListing, member: MockMember): number {
    const base = deliveryFeeOf(listing);
    return member.dongCode && listing.dongCode && member.dongCode !== listing.dongCode ? base + 5_000 : base;
}

const TOKEN_PREFIX = 'mock-token-';

function error(status: number, code: string, message: string) {
    return HttpResponse.json({ code, message }, { status });
}

function currentMember(request: Request): MockMember | null {
    const token = request.headers.get('Authorization')?.replace('Bearer ', '') ?? '';
    if (!token.startsWith(TOKEN_PREFIX)) return null;
    return members.find((member) => member.loginId === token.slice(TOKEN_PREFIX.length)) ?? null;
}

const unauthorized = () => error(401, 'INVALID_TOKEN', '로그인이 필요합니다');

function dongOf(code: string | null) {
    return dongs.find((dong) => dong.code === code) ?? null;
}

function hasPurchaseRequest(listing: MockListing): boolean {
    return orders.some((order) => order.listingId === listing.id && order.orderStatus === 'PENDING');
}

function orderStateOf(listing: MockListing): 'NONE' | 'PAYING' | 'TRADING' {
    const open = orders.filter((order) => order.listingId === listing.id);
    if (listing.saleStatus === 'RESERVED' || open.some((order) => order.orderStatus === 'CONFIRMED' || order.orderStatus === 'CANCEL_PENDING')) return 'TRADING';
    if (open.some((order) => order.orderStatus === 'PENDING')) return 'PAYING';
    return 'NONE';
}

function myOpenOrderOf(listing: MockListing, member: MockMember | null): MockOrder | undefined {
    if (!member) return undefined;
    return orders.find((order) => order.listingId === listing.id && order.buyerId === member.id
        && (order.orderStatus === 'PENDING' || order.orderStatus === 'CONFIRMED' || order.orderStatus === 'CANCEL_PENDING'));
}

function favoriteCountOf(listing: MockListing): number {
    return [...favorites.values()].filter((ids) => ids.has(listing.id)).length;
}

function summaryOf(listing: MockListing) {
    const deliveryFee = deliveryFeeOf(listing);
    return {
        id: listing.id,
        title: listing.title,
        thumbnailUrl: listing.images[0]?.url ?? null,
        price: listing.price,
        deliveryFee,
        totalPrice: listing.price + deliveryFee,
        category: listing.category,
        conditionGrade: listing.conditionGrade,
        createdAt: listing.createdAt,
        dong: dongOf(listing.dongCode),
        hasPurchaseRequest: hasPurchaseRequest(listing),
        orderState: orderStateOf(listing),
        favoriteCount: favoriteCountOf(listing)
    };
}

function pendingOrderOf(listing: MockListing, member: MockMember | null): MockOrder | undefined {
    if (!member) return undefined;
    return orders.find((order) => order.listingId === listing.id && order.buyerId === member.id && order.orderStatus === 'PENDING');
}

function detailOf(listing: MockListing, member: MockMember | null) {
    return {
        ...summaryOf(listing),
        description: listing.description,
        dimensions: listing.dimensions,
        saleStatus: listing.saleStatus,
        images: listing.images,
        updatedAt: listing.updatedAt,
        purchaseRequestedByMe: pendingOrderOf(listing, member) !== undefined,
        myOrderId: myOpenOrderOf(listing, member)?.id ?? null
    };
}

function aboutMe(member: MockMember) {
    return {
        id: member.id,
        loginId: member.loginId,
        phoneNumber: member.phoneNumber,
        address: member.address,
        role: 'MEMBER',
        dongCode: member.dongCode
    };
}

function orderOf(orderId: number) {
    const order = orders.find((item) => item.id === orderId);
    if (!order) return null;
    const listing = listings.find((item) => item.id === order.listingId);
    return {
        id: order.id,
        listing: listing
            ? { id: listing.id, name: listing.title, thumbnailUrl: listing.images[0]?.url ?? null, price: listing.price, deliveryFee: order.deliveryFee }
            : null,
        orderStatus: order.orderStatus,
        deliveryStatus: order.deliveryStatus
    };
}

function activeListing(id: number) {
    return listings.find((listing) => listing.id === id && !listing.deleted) ?? null;
}

async function readListingForm(request: Request) {
    const formData = await request.formData();
    const requestPart = formData.get('request');
    const payload = JSON.parse(requestPart instanceof Blob ? await requestPart.text() : String(requestPart)) as {
        title: string;
        description: string;
        price: number;
        category: string;
        conditionGrade: string;
        dimensions: MockListing['dimensions'];
        retainedImageIds?: number[];
    };
    const files = formData.getAll('images').filter((value): value is File => value instanceof File);
    return { payload, files };
}

export const handlers = [
    http.post('/api/auth/login', async ({ request }) => {
        await delay(400);
        const { loginId, password } = await request.json() as { loginId: string; password: string };
        const member = members.find((item) => item.loginId === loginId && item.password === password);
        if (!member) return error(401, 'LOGIN_FAILED', '아이디 또는 비밀번호가 올바르지 않습니다');
        return HttpResponse.json({ token: TOKEN_PREFIX + member.loginId, role: 'MEMBER' });
    }),

    http.post('/api/auth/signup', async ({ request }) => {
        await delay(400);
        const body = await request.json() as Omit<MockMember, 'id'>;
        if (members.some((member) => member.loginId === body.loginId)) {
            return error(409, 'DUPLICATE_LOGIN_ID', '이미 사용 중인 아이디입니다');
        }
        const member = { ...body, id: members.length + 1 };
        members.push(member);
        return HttpResponse.json({ id: member.id, loginId: member.loginId, role: 'MEMBER' }, { status: 201 });
    }),

    http.get('/api/auth/me', async ({ request }) => {
        await delay(200);
        const member = currentMember(request);
        return member ? HttpResponse.json(aboutMe(member)) : unauthorized();
    }),

    http.put('/api/auth/me', async ({ request }) => {
        await delay(300);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const { phoneNumber, address } = await request.json() as { phoneNumber: string; address: string };
        member.phoneNumber = phoneNumber;
        member.address = address;
        return HttpResponse.json(aboutMe(member));
    }),

    http.put('/api/auth/me/dong', async ({ request }) => {
        await delay(200);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const { dongCode } = await request.json() as { dongCode: string };
        if (!dongOf(dongCode)) return error(400, 'DELIVERY_AREA_NOT_SUPPORTED', '배송 가능 지역이 아닙니다');
        member.dongCode = dongCode;
        return HttpResponse.json(aboutMe(member));
    }),

    http.post('/api/auth/logout', () => new HttpResponse(null, { status: 204 })),

    http.get('/api/dongs', async () => {
        await delay(300);
        return HttpResponse.json({ items: dongs });
    }),

    http.get('/api/listings', async ({ request }) => {
        await delay(500);
        const url = new URL(request.url);
        const available = listings
            .filter((listing) => !listing.deleted && listing.saleStatus === 'AVAILABLE')
            .sort((left, right) => right.createdAt.localeCompare(left.createdAt) || right.id - left.id);
        const maxDeliveryFee = url.searchParams.get('maxDeliveryFee');
        const category = url.searchParams.get('category');
        const query = url.searchParams.get('query')?.trim().toLowerCase();
        if (maxDeliveryFee !== null) {
            const member = currentMember(request);
            if (!member) return unauthorized();
            if (!member.dongCode) return error(400, 'MEMBER_DONG_REQUIRED', '행정동을 먼저 설정해 주세요');
        }
        const filtered = available.filter((listing) =>
            (maxDeliveryFee === null || deliveryFeeOf(listing) <= Number(maxDeliveryFee))
            && (!category || listing.category === category)
            && (!query || listing.title.toLowerCase().includes(query)));
        if (!url.searchParams.has('size') && !url.searchParams.has('cursor') && maxDeliveryFee === null && !category && !query) {
            return HttpResponse.json({ items: filtered.map(summaryOf) });
        }
        const size = Number(url.searchParams.get('size') ?? 20);
        const cursor = Number(url.searchParams.get('cursor') ?? 0);
        const page = filtered.slice(cursor, cursor + size);
        const nextCursor = cursor + size < filtered.length ? String(cursor + size) : null;
        return HttpResponse.json({ items: page.map(summaryOf), nextCursor });
    }),

    http.get('/api/listings/:listingId', async ({ params, request }) => {
        await delay(300);
        const listing = activeListing(Number(params.listingId));
        if (!listing) return error(404, 'LISTING_NOT_FOUND', '매물을 찾을 수 없습니다');
        return HttpResponse.json(detailOf(listing, currentMember(request)));
    }),

    http.post('/api/listings', async ({ request }) => {
        await delay(700);
        const member = currentMember(request);
        if (!member) return unauthorized();
        if (!member.dongCode) return error(400, 'MEMBER_DONG_REQUIRED', '행정동을 먼저 설정해 주세요');
        const { payload, files } = await readListingForm(request);
        const createdAt = new Date().toISOString();
        const listing: MockListing = {
            id: Math.max(...listings.map((item) => item.id)) + 1,
            sellerId: member.id,
            title: payload.title,
            description: payload.description,
            price: payload.price,
            category: payload.category,
            conditionGrade: payload.conditionGrade,
            dimensions: payload.dimensions,
            saleStatus: 'AVAILABLE',
            images: files.map((file, index) => ({ id: newImageId(), url: URL.createObjectURL(file), displayOrder: index })),
            dongCode: member.dongCode,
            createdAt,
            updatedAt: createdAt,
            deleted: false
        };
        listings.push(listing);
        return HttpResponse.json({ listingId: listing.id, createdAt }, { status: 201 });
    }),

    http.put('/api/listings/:listingId', async ({ request, params }) => {
        await delay(700);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const listing = activeListing(Number(params.listingId));
        if (!listing || listing.sellerId !== member.id) return error(404, 'LISTING_NOT_FOUND', '매물을 찾을 수 없습니다');
        const { payload, files } = await readListingForm(request);
        const retained = (payload.retainedImageIds ?? [])
            .map((id) => listing.images.find((image) => image.id === id))
            .filter((image): image is MockListing['images'][number] => image !== undefined);
        const added = files.map((file) => ({ id: newImageId(), url: URL.createObjectURL(file), displayOrder: 0 }));
        Object.assign(listing, {
            title: payload.title,
            description: payload.description,
            price: payload.price,
            category: payload.category,
            conditionGrade: payload.conditionGrade,
            dimensions: payload.dimensions,
            images: [...retained, ...added].map((image, index) => ({ ...image, displayOrder: index })),
            updatedAt: new Date().toISOString()
        });
        return new HttpResponse(null, { status: 204 });
    }),

    http.delete('/api/listings/:listingId', async ({ request, params }) => {
        await delay(500);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const listing = activeListing(Number(params.listingId));
        if (!listing || listing.sellerId !== member.id) return error(404, 'LISTING_NOT_FOUND', '매물을 찾을 수 없습니다');
        listing.deleted = true;
        return new HttpResponse(null, { status: 204 });
    }),

    http.get('/api/me/listings', async ({ request }) => {
        await delay(300);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const mine = listings
            .filter((listing) => !listing.deleted && listing.sellerId === member.id)
            .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
            .map((listing) => {
                const purchaseRequested = hasPurchaseRequest(listing);
                const isEditable = listing.saleStatus === 'AVAILABLE' && !purchaseRequested;
                return {
                    ...summaryOf(listing),
                    saleStatus: listing.saleStatus,
                    hasPurchaseRequest: purchaseRequested,
                    canUpdate: isEditable,
                    canDelete: isEditable
                };
            });
        return HttpResponse.json({ items: mine });
    }),

    http.get('/api/me/favorites', async ({ request }) => {
        await delay(300);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const ids = favorites.get(member.id) ?? new Set<number>();
        const items = [...ids].map(activeListing).filter((listing): listing is MockListing => listing !== null).map(summaryOf);
        return HttpResponse.json({ items });
    }),

    http.get('/api/me/favorites/:listingId', ({ request, params }) => {
        const member = currentMember(request);
        if (!member) return unauthorized();
        return HttpResponse.json({ favorited: favorites.get(member.id)?.has(Number(params.listingId)) ?? false });
    }),

    http.put('/api/me/favorites/:listingId', async ({ request, params }) => {
        await delay(200);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const ids = favorites.get(member.id) ?? new Set<number>();
        ids.add(Number(params.listingId));
        favorites.set(member.id, ids);
        return new HttpResponse(null, { status: 204 });
    }),

    http.delete('/api/me/favorites/:listingId', async ({ request, params }) => {
        await delay(200);
        const member = currentMember(request);
        if (!member) return unauthorized();
        favorites.get(member.id)?.delete(Number(params.listingId));
        return new HttpResponse(null, { status: 204 });
    }),

    http.post('/api/orders', async ({ request }) => {
        await delay(400);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const { listingId } = await request.json() as { listingId: number };
        const listing = activeListing(listingId);
        if (!listing || listing.saleStatus !== 'AVAILABLE') return error(409, 'LISTING_NOT_AVAILABLE', '구매할 수 없는 매물입니다');
        // 서버처럼 같은 구매자의 결제 대기 주문은 새로 만들지 않고 그대로 돌려준다
        const mine = pendingOrderOf(listing, member);
        if (mine) return HttpResponse.json({ ...mine, deliveryStatus: 'PENDING' }, { status: 201 });
        if (hasPurchaseRequest(listing)) return error(409, 'ALREADY_ORDERED', '이미 결제가 진행 중인 매물입니다');
        const deliveryFee = deliveryFeeOf(listing);
        const order = {
            id: Math.max(...orders.map((item) => item.id)) + 1,
            listingId,
            buyerId: member.id,
            orderStatus: 'PENDING' as const,
            deliveryStatus: 'PENDING' as const,
            deliveryFee,
            totalPrice: listing.price + deliveryFee
        };
        orders.push(order);
        return HttpResponse.json({ ...order, deliveryStatus: 'PENDING' }, { status: 201 });
    }),

    http.get('/api/me/orders', async ({ request }) => {
        await delay(300);
        const member = currentMember(request);
        if (!member) return unauthorized();
        return HttpResponse.json(
            orders.filter((order) => order.buyerId === member.id).sort((left, right) => right.id - left.id).map((order) => orderOf(order.id))
        );
    }),

    http.get('/api/orders/:orderId', async ({ request, params }) => {
        await delay(300);
        if (!currentMember(request)) return unauthorized();
        const order = orderOf(Number(params.orderId));
        return order ? HttpResponse.json(order) : error(404, 'ORDER_NOT_FOUND', '주문을 찾을 수 없습니다');
    }),

    http.post('/api/orders/:orderId/cancellations', async ({ request, params }) => {
        await delay(400);
        if (!currentMember(request)) return unauthorized();
        const order = orders.find((item) => item.id === Number(params.orderId));
        if (!order) return error(404, 'ORDER_NOT_FOUND', '주문을 찾을 수 없습니다');
        if (order.orderStatus !== 'CONFIRMED' || order.deliveryStatus !== 'REQUESTED') {
            return error(409, 'ORDER_NOT_CANCELLABLE', '배송이 시작된 주문은 취소할 수 없습니다');
        }
        // 배송 쪽이 취소를 처리하는 동안은 취소 대기로 두었다가 잠시 뒤 취소를 확정한다
        order.orderStatus = 'CANCEL_PENDING';
        setTimeout(() => {
            order.orderStatus = 'CANCELLED';
            const listing = listings.find((item) => item.id === order.listingId);
            if (listing) listing.saleStatus = 'AVAILABLE';
        }, 2_000);
        return HttpResponse.json({ orderId: order.id, orderStatus: order.orderStatus, message: '취소 요청을 접수했습니다' }, { status: 202 });
    }),

    http.post('/api/payments/confirm', async ({ request }) => {
        await delay(800);
        if (!currentMember(request)) return unauthorized();
        const { orderId, amount } = await request.json() as { orderId: string; amount: number };
        const order = orders.find((item) => item.id === Number(orderId.split('_')[0]));
        if (!order) return error(404, 'ORDER_NOT_FOUND', '주문을 찾을 수 없습니다');
        if (order.totalPrice !== amount) return error(400, 'PAYMENT_AMOUNT_MISMATCH', '결제 금액이 주문 금액과 다릅니다');
        order.orderStatus = 'CONFIRMED';
        order.deliveryStatus = 'REQUESTED';
        const listing = listings.find((item) => item.id === order.listingId);
        if (listing) listing.saleStatus = 'SOLD';
        return HttpResponse.json({ orderId: order.id, status: 'DONE', amount });
    }),

    http.get('/api/me/keywords', async ({ request }) => {
        await delay(300);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const mine = keywords
            .filter((keyword) => keyword.memberId === member.id)
            .sort((left, right) => left.createdAt.localeCompare(right.createdAt) || left.id - right.id);
        return HttpResponse.json({ items: mine.map(keywordOf) });
    }),

    http.put('/api/me/keywords', async ({ request }) => {
        await delay(300);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const keyword = ((await request.json() as { keyword?: string }).keyword ?? '').trim();
        if (!keyword || keyword.length > 20) return error(400, 'INVALID_KEYWORD', '키워드는 공백을 제외하고 1자 이상 20자 이하여야 합니다');
        const mine = keywords.filter((item) => item.memberId === member.id);
        if (mine.some((item) => item.keyword.toLowerCase() === keyword.toLowerCase())) return new HttpResponse(null, { status: 204 });
        if (mine.length >= MAX_KEYWORDS) return error(400, 'TOO_MANY_KEYWORDS', '키워드는 10개까지 등록할 수 있습니다');
        keywords.push({
            id: newKeywordId(),
            memberId: member.id,
            keyword,
            category: null,
            minPrice: null,
            maxPrice: null,
            maxDeliveryFee: null,
            maxTotalPrice: null,
            excludeKeywords: [],
            createdAt: new Date().toISOString()
        });
        return new HttpResponse(null, { status: 204 });
    }),

    http.put('/api/me/keywords/:keywordId/conditions', async ({ request, params }) => {
        await delay(300);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const keyword = keywords.find((item) => item.id === Number(params.keywordId) && item.memberId === member.id);
        if (!keyword) return error(404, 'KEYWORD_NOT_FOUND', '존재하지 않는 키워드입니다');
        const body = await request.json() as ConditionsBody;
        const message = conditionsError(keyword, body);
        if (message) return error(400, 'INVALID_KEYWORD_CONDITION', message);
        Object.assign(keyword, {
            category: body.category?.trim().toUpperCase() || null,
            minPrice: body.minPrice ?? null,
            maxPrice: body.maxPrice ?? null,
            maxDeliveryFee: body.maxDeliveryFee ?? null,
            maxTotalPrice: body.maxTotalPrice ?? null,
            excludeKeywords: normalizedExcludes(body)
        });
        return new HttpResponse(null, { status: 204 });
    }),

    http.delete('/api/me/keywords/:keywordId', async ({ request, params }) => {
        await delay(200);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const index = keywords.findIndex((item) => item.id === Number(params.keywordId) && item.memberId === member.id);
        if (index < 0) return error(404, 'KEYWORD_NOT_FOUND', '존재하지 않는 키워드입니다');
        keywords.splice(index, 1);
        return new HttpResponse(null, { status: 204 });
    }),

    http.get('/api/me/notifications', async ({ request }) => {
        await delay(300);
        const member = currentMember(request);
        if (!member) return unauthorized();
        const url = new URL(request.url);
        const size = Math.min(Math.max(Number(url.searchParams.get('size') ?? 20), 1), 100);
        const page = Math.max(Number(url.searchParams.get('page') ?? 0), 0);
        const mine = notifications
            .filter((notification) => notification.memberId === member.id)
            .sort((left, right) => right.createdAt.localeCompare(left.createdAt) || right.id - left.id)
            .slice(page * size, page * size + size)
            .map((notification) => {
                const listing = listings.find((item) => item.id === notification.listingId);
                return {
                    id: notification.id,
                    type: 'KEYWORD',
                    listingId: notification.listingId,
                    title: listing?.title ?? null,
                    price: listing?.price ?? null,
                    deliveryFee: listing ? deliveryFeeFor(listing, member) : null,
                    listingStatus: notifiedStatusOf(listing),
                    status: notification.status,
                    read: notification.readAt !== null,
                    createdAt: notification.createdAt
                };
            });
        return HttpResponse.json({ items: mine });
    }),

    http.get('/api/me/notifications/unread-count', ({ request }) => {
        const member = currentMember(request);
        if (!member) return unauthorized();
        const count = notifications.filter((item) => item.memberId === member.id && item.status === 'SENT' && !item.readAt).length;
        return HttpResponse.json({ count });
    }),

    http.post('/api/me/notifications/read', ({ request }) => {
        const member = currentMember(request);
        if (!member) return unauthorized();
        const readAt = new Date().toISOString();
        notifications
            .filter((item) => item.memberId === member.id && item.status === 'SENT')
            .forEach((item) => { item.readAt ??= readAt; });
        return new HttpResponse(null, { status: 204 });
    }),

    http.post('/api/me/notifications/:notificationId/read', ({ request, params }) => {
        const member = currentMember(request);
        if (!member) return unauthorized();
        const notification = notifications.find((item) => item.id === Number(params.notificationId) && item.memberId === member.id);
        if (!notification) return error(404, 'NOTIFICATION_NOT_FOUND', '존재하지 않는 알림입니다');
        notification.readAt ??= new Date().toISOString();
        return new HttpResponse(null, { status: 204 });
    }),

    http.post('/api/me/notifications/:notificationId/ack', async ({ request, params }) => {
        const member = currentMember(request);
        if (!member) return unauthorized();
        const notification = notifications.find((item) => item.id === Number(params.notificationId) && item.memberId === member.id);
        if (!notification) return error(404, 'NOTIFICATION_NOT_FOUND', '존재하지 않는 알림입니다');
        notification.ackedAt ??= new Date().toISOString();
        return new HttpResponse(null, { status: 204 });
    }),

    http.put('/api/me/device-token', async ({ request }) => {
        if (!currentMember(request)) return unauthorized();
        return new HttpResponse(null, { status: 204 });
    }),

    http.delete('/api/me/device-token', async ({ request }) => {
        if (!currentMember(request)) return unauthorized();
        return new HttpResponse(null, { status: 204 });
    }),

    http.post('/api/payments/fail', async ({ request }) => {
        const { orderId } = await request.json() as { orderId: string };
        const order = orders.find((item) => item.id === Number(orderId.split('_')[0]));
        if (order?.orderStatus === 'PENDING') order.orderStatus = 'EXPIRED';
        return new HttpResponse(null, { status: 204 });
    })
];

function notifiedStatusOf(listing: MockListing | undefined): string | null {
    if (!listing) return null;
    if (listing.deleted) return 'DELETED';
    if (listing.saleStatus === 'SOLD') return 'SOLD';
    return orderStateOf(listing) === 'NONE' ? 'AVAILABLE' : 'TRADING';
}
