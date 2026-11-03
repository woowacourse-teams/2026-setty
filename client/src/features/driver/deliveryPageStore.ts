import { isUnauthorized, toLoadError, toUserMessage, type LoadState } from '../../api/errors';
import { deliveryRepository } from '../../api/deliveryRepository';
import { deliveryTokenStore } from '../../api/http';
import type { DeliveryPage, DeliverySummary } from '../../model/delivery';
import { showSnackbar } from '../../ui/snackbar';

type PageState = {
    state: LoadState<DeliverySummary[]>;
    nextCursor: string | null;
    isRefreshing: boolean;
    isLoadingMore: boolean;
    hasLoadMoreError: boolean;
};

const initialState: PageState = {
    state: { status: 'loading' }, nextCursor: null, isRefreshing: false, isLoadingMore: false, hasLoadMoreError: false
};

/** 배차 요청과 내 배차의 상태별 목록. 상세에 다녀와도 이미 읽은 페이지를 유지한다. */
export function createDeliveryPageStore(fetchPage: (cursor: string | null) => Promise<DeliveryPage>) {
    let snapshot = initialState;
    let account: string | null = null;
    let version = 0;
    let refreshing: number | null = null;
    let refreshQueued = false;
    let onUnauthorized = () => {};
    const listeners = new Set<() => void>();

    function update(patch: Partial<PageState>) {
        snapshot = { ...snapshot, ...patch };
        listeners.forEach((listener) => listener());
    }

    function isCurrent(request: number) {
        return request === version && account === deliveryTokenStore.get();
    }

    function fail(error: unknown) {
        if (snapshot.state.status !== 'success') update({ state: toLoadError(error) });
        if (isUnauthorized(error)) onUnauthorized();
        else if (snapshot.state.status === 'success') showSnackbar(toUserMessage(error));
    }

    async function refresh() {
        // 연속 SSE·폴링은 현재 조회가 끝난 뒤 한 번으로 합친다.
        if (refreshing !== null) {
            refreshQueued = true;
            return;
        }
        const request = ++version;
        refreshing = request;
        const retainCount = snapshot.state.status === 'success' ? snapshot.state.data.length : 0;
        update({
            isRefreshing: true, isLoadingMore: false, hasLoadMoreError: false,
            ...(snapshot.state.status !== 'success' ? { state: { status: 'loading' } as const } : {})
        });
        try {
            const page = await fetchPage(null);
            if (!isCurrent(request)) return;
            let items = unique([], page.items);
            let cursor = page.nextCursor;
            while (cursor !== null && items.length < retainCount) {
                const next = await fetchPage(cursor);
                if (!isCurrent(request)) return;
                items = unique(items, next.items);
                cursor = next.nextCursor;
            }
            update({ state: { status: 'success', data: items }, nextCursor: cursor });
        } catch (error) {
            if (isCurrent(request)) fail(error);
        } finally {
            if (refreshing === request) {
                refreshing = null;
                update({ isRefreshing: false });
                if (refreshQueued && account === deliveryTokenStore.get()) {
                    refreshQueued = false;
                    void refresh();
                }
            }
        }
    }

    async function loadMore() {
        const cursor = snapshot.nextCursor;
        if (cursor === null || snapshot.state.status !== 'success' || snapshot.isRefreshing || snapshot.isLoadingMore) return;
        const request = ++version;
        update({ isLoadingMore: true, hasLoadMoreError: false });
        try {
            const page = await fetchPage(cursor);
            if (!isCurrent(request) || snapshot.state.status !== 'success') return;
            update({ state: { status: 'success', data: unique(snapshot.state.data, page.items) }, nextCursor: page.nextCursor });
        } catch (error) {
            if (isCurrent(request)) {
                update({ hasLoadMoreError: true });
                fail(error);
            }
        } finally {
            if (isCurrent(request)) update({ isLoadingMore: false });
        }
    }

    return {
        getSnapshot: () => snapshot,
        subscribe(listener: () => void) {
            listeners.add(listener);
            return () => { listeners.delete(listener); };
        },
        enter(navigateToLogin: () => void) {
            const token = deliveryTokenStore.get();
            if (account !== token) {
                ++version;
                account = token;
                refreshing = null;
                refreshQueued = false;
                snapshot = initialState;
            }
            onUnauthorized = navigateToLogin;
            void refresh();
        },
        refresh,
        loadMore
    };
}

function unique(current: DeliverySummary[], incoming: DeliverySummary[]) {
    const seen = new Set(current.map((item) => item.deliveryId));
    return [...current, ...incoming.filter((item) => {
        if (seen.has(item.deliveryId)) return false;
        seen.add(item.deliveryId);
        return true;
    })];
}

export const requestPageStore = createDeliveryPageStore((cursor) => deliveryRepository.getRequestPage(cursor));
export const inProgressPageStore = createDeliveryPageStore((cursor) => deliveryRepository.getShipmentPage('IN_PROGRESS', cursor));
export const deliveredPageStore = createDeliveryPageStore((cursor) => deliveryRepository.getShipmentPage('DELIVERED', cursor));
