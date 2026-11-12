import { ApiError, tokenStore } from '../../api/http';

export type SupportInput = { type: 'TEXT'; text: string } | { type: 'SELECT_ORDER' | 'CONFIRM_CANCEL' | 'DECLINE'; sourceTurnId: number; orderId: number } | { type: 'NEXT_ORDERS'; sourceTurnId: number };
export type SupportOrder = { orderId: number; title: string | null; createdAt: string | null; createdDateKst?: string | null; orderStatus: string; thumbnailUrl?: string | null; totalPrice?: number | null; deliveryStatus?: string | null };
export type SupportCard =
    | { type: 'ORDERS'; orders: SupportOrder[]; nextCursor: number | null; hasUndatedOrders: boolean; excludedUndatedCount?: number; pageSize?: number; confirmLatest?: boolean; purpose?: 'BROWSE' | 'CANCEL_CHECK' | 'CANCELLED_CHECK' }
    | { type: 'ELIGIBILITY'; order: SupportOrder; deliveryStatus: string | null; eligibility: 'ELIGIBLE' | 'INELIGIBLE' | 'UNKNOWN'; reasonCode: string; observedAt: string; policyVersion: string; confirmationOnly?: boolean; cancellationOffer?: boolean; confirmationExpiresAt?: string }
    | { type: 'CANCELLATION_REQUEST'; order: SupportOrder; requestId: string; acceptedAt: string; status: 'ACCEPTED'; historical?: boolean; previousStatus?: 'ACCEPTED' | 'COMPLETED' | 'REJECTED' };
export type SupportTurn = { turnId: number; clientRequestId: string; input: SupportInput; status: 'PROCESSING' | 'SUCCEEDED' | 'FAILED'; createdAt: string; processingDeadlineAt: string; answer: string | null; cards: SupportCard[]; errorCode: string | null };
export type Conversation = { id: number; title: string; createdAt: string; lastActivityAt: string; expiresAt: string };
export type ConversationListItem = Conversation & { hasMessages?: boolean };
export type ConversationPage = { items: ConversationListItem[]; nextCursor: string | null };
export type TurnPage = { conversation: Conversation; items: SupportTurn[]; nextCursor: number | null; activeTurn: SupportTurn | null };
export type MessageRequest = { clientRequestId: string; input: SupportInput };

/** 서버 접수 여부를 단정할 수 없는 오류. 같은 요청 ID로만 다시 전송한다. */
export class UncertainSupportResponse extends Error {
    constructor() { super('응답을 확인하지 못했어요. 처리 기록을 확인하거나 같은 요청을 다시 확인해 주세요.'); }
}
const object = (x: unknown): x is Record<string, unknown> => typeof x === 'object' && x !== null;
const id = (x: unknown): x is number => typeof x === 'number' && Number.isSafeInteger(x) && x > 0;
const date = (x: unknown): x is string => typeof x === 'string' && Number.isFinite(Date.parse(x));
const nullableText = (x: unknown) => x === null || typeof x === 'string';
const cursor = (x: unknown) => x === null || id(x);
function order(x: unknown): x is SupportOrder { return object(x) && id(x.orderId) && nullableText(x.title) && (x.createdAt === null || date(x.createdAt)) && (x.createdDateKst === undefined || x.createdDateKst === null || (typeof x.createdDateKst === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(x.createdDateKst))) && typeof x.orderStatus === 'string' && (x.thumbnailUrl === undefined || nullableText(x.thumbnailUrl)) && (x.totalPrice === undefined || x.totalPrice === null || (typeof x.totalPrice === 'number' && Number.isSafeInteger(x.totalPrice) && x.totalPrice >= 0)) && (x.deliveryStatus === undefined || nullableText(x.deliveryStatus)); }
function input(x: unknown): x is SupportInput {
    return object(x) && ((x.type === 'TEXT' && typeof x.text === 'string') || ((x.type === 'SELECT_ORDER' || x.type === 'CONFIRM_CANCEL' || x.type === 'DECLINE') && id(x.sourceTurnId) && id(x.orderId)) || (x.type === 'NEXT_ORDERS' && id(x.sourceTurnId)));
}
function card(x: unknown): x is SupportCard {
    if (!object(x)) return false;
    if (x.type === 'ORDERS') return ordersCard(x);
    if (x.type === 'CANCELLATION_REQUEST') return cancellationRequestCard(x);
    return eligibilityCard(x);
}
function ordersCard(x: Record<string, unknown>): boolean {
    return (x.excludedUndatedCount === undefined || (typeof x.excludedUndatedCount === 'number'
        && Number.isSafeInteger(x.excludedUndatedCount)
        && x.excludedUndatedCount >= 0))
        && (x.confirmLatest === undefined || typeof x.confirmLatest === 'boolean')
        && Array.isArray(x.orders)
        && x.orders.every(order)
        && cursor(x.nextCursor)
        && typeof x.hasUndatedOrders === 'boolean'
        && (x.pageSize === undefined || (id(x.pageSize)
        && x.pageSize <= 20))
        && (x.purpose === undefined || x.purpose === 'BROWSE' || x.purpose === 'CANCEL_CHECK' || x.purpose === 'CANCELLED_CHECK');
}
function cancellationRequestCard(x: Record<string, unknown>): boolean {
    return (x.historical === undefined || typeof x.historical === 'boolean')
        && (x.previousStatus === undefined || ['ACCEPTED','COMPLETED','REJECTED'].includes(String(x.previousStatus)))
        && order(x.order)
        && typeof x.requestId === 'string'
        && x.requestId.length > 0
        && date(x.acceptedAt)
        && x.status === 'ACCEPTED';
}
function eligibilityCard(x: Record<string, unknown>): boolean {
    return (x.confirmationOnly === undefined || typeof x.confirmationOnly === 'boolean')
        && (x.cancellationOffer === undefined || (typeof x.cancellationOffer === 'boolean'
        && date(x.confirmationExpiresAt)))
        && x.type === 'ELIGIBILITY'
        && order(x.order)
        && nullableText(x.deliveryStatus)
        && ['ELIGIBLE', 'INELIGIBLE', 'UNKNOWN'].includes(String(x.eligibility))
        && typeof x.reasonCode === 'string'
        && date(x.observedAt)
        && typeof x.policyVersion === 'string';
}
export function isTurn(x: unknown): x is SupportTurn {
    return object(x) && id(x.turnId) && typeof x.clientRequestId === 'string' && input(x.input) && ['PROCESSING', 'SUCCEEDED', 'FAILED'].includes(String(x.status)) && date(x.createdAt) && date(x.processingDeadlineAt) && nullableText(x.answer) && Array.isArray(x.cards) && x.cards.every(card) && nullableText(x.errorCode);
}
export function isConversation(x: unknown): x is Conversation { return object(x) && id(x.id) && typeof x.title === 'string' && date(x.createdAt) && date(x.lastActivityAt) && date(x.expiresAt); }
export function isTurnPage(x: unknown): x is TurnPage { return object(x) && isConversation(x.conversation) && Array.isArray(x.items) && x.items.every(isTurn) && cursor(x.nextCursor) && (x.activeTurn === null || isTurn(x.activeTurn)); }
export function isConversationPage(x: unknown): x is ConversationPage { return object(x) && Array.isArray(x.items) && x.items.every((item: unknown) => isConversation(item) && ((item as ConversationListItem).hasMessages === undefined || typeof (item as ConversationListItem).hasMessages === 'boolean')) && (x.nextCursor === null || typeof x.nextCursor === 'string'); }

export type CancellationOutcome = { requestId: string; orderId: number; status: 'ACCEPTED' | 'COMPLETED' | 'REJECTED'; acceptedAt: string; decidedAt: string | null; reasonCode: string | null };
export function isCancellationOutcome(x: unknown): x is CancellationOutcome {
    return object(x) && typeof x.requestId === 'string' && x.requestId.length > 0 && id(x.orderId) && date(x.acceptedAt)
        && ['ACCEPTED','COMPLETED','REJECTED'].includes(String(x.status)) && nullableText(x.reasonCode)
        && (x.status === 'ACCEPTED' ? x.decidedAt === null : date(x.decidedAt));
}

async function request<T>(path: string, validate: (x: unknown) => x is T, method = 'GET', body?: unknown, options: { base?: string; signal?: AbortSignal; timeoutMs?: number } = {}): Promise<T> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 70000);
    const abort = () => controller.abort();
    if (options.signal?.aborted) controller.abort();
    options.signal?.addEventListener('abort', abort, { once: true });
    const ownerToken = tokenStore.get();
    try {
        const response = await fetch(`/api/support/${options.base ?? 'conversations'}${path}`, {
            method, signal: controller.signal,
            headers: { Authorization: `Bearer ${ownerToken}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
            ...(body ? { body: JSON.stringify(body) } : {})
        });
        if (ownerToken !== tokenStore.get()) throw new ApiError('SUPPORT_SESSION_CHANGED', '로그인 상태가 바뀌었어요.', 401);
        const value: unknown = response.status === 204 ? null : await response.json();
        if (ownerToken !== tokenStore.get()) throw new ApiError('SUPPORT_SESSION_CHANGED', '로그인 상태가 바뀌었어요.', 401);
        if (!response.ok) throw new ApiError(object(value) && typeof value.code === 'string' ? value.code : 'SUPPORT_ERROR', object(value) && typeof value.message === 'string' ? value.message : '상담을 불러오지 못했어요.', response.status);
        if (!validate(value)) throw new UncertainSupportResponse();
        return value;
    } catch (error) {
        if (error instanceof ApiError) throw error;
        throw new UncertainSupportResponse();
    } finally { clearTimeout(timeout); options.signal?.removeEventListener('abort',abort); }
}
export const supportApi = {
    cancellation: (requestId: string, signal?: AbortSignal) => request(`/${encodeURIComponent(requestId)}`,isCancellationOutcome,'GET',undefined,{base:'cancellations',signal,timeoutMs:5000}),
    create: () => request('', isConversation, 'POST'),
    list: (cursor?: string) => request(cursor ? `?cursor=${encodeURIComponent(cursor)}` : '', isConversationPage),
    detail: (conversationId: number, cursor?: number) => request(`/${conversationId}${cursor ? `?cursor=${cursor}` : ''}`, isTurnPage),
    send: (conversationId: number, body: MessageRequest) => request(`/${conversationId}/messages`, isTurn, 'POST', body),
    delete: (conversationId: number) => request(`/${conversationId}`, (x): x is null => x === null, 'DELETE')
};
