import { useSyncExternalStore } from 'react';
import { isMemberDongRequired } from '../../api/errors';
import { furnitureRepository, userRepository } from '../../api/repositories';
import type { FurnitureCategory, FurniturePage, FurnitureSummary } from '../../model/furniture';
import { showSnackbar } from '../../ui/snackbar';

export type HomeState = {
    furnitures: FurnitureSummary[];
    selectedCategory: FurnitureCategory | null;
    // 고른 배송비 예산. null이면 거르지 않고 전체를 한 번에 받는다
    maxDeliveryFee: number | null;
    // 배송비로 거를 때 이어 받을 다음 페이지 커서. 마지막 페이지면 null
    nextCursor: string | null;
    isLoadingMore: boolean;
    // 프로필 버튼에 보여줄 아이디 첫 글자. 회원 정보를 아직 못 받았으면 null
    profileInitial: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    hasError: boolean;
};

const initialState: HomeState = {
    furnitures: [],
    selectedCategory: null,
    maxDeliveryFee: null,
    nextCursor: null,
    isLoadingMore: false,
    profileInitial: null,
    isLoading: true,
    isRefreshing: false,
    hasError: false
};

let state: HomeState = initialState;
// 지금 보여주는 목록을 받은 배송비 예산. 다른 예산으로 받다 실패하면 필터를 이 값으로 되돌린다
let shownMaxDeliveryFee: number | null = null;
// 필터를 바꾸면 이전 요청의 결과가 뒤늦게 덮어쓰지 않도록 요청마다 번호를 붙인다
let listingsRequest = 0;
let onRequireDong: () => void = () => undefined;
const listeners = new Set<() => void>();

function update(patch: Partial<HomeState> | ((current: HomeState) => Partial<HomeState>)) {
    const next = typeof patch === 'function' ? patch(state) : patch;
    state = { ...state, ...next };
    listeners.forEach((listener) => listener());
}

export function useHomeState(): HomeState {
    return useSyncExternalStore(
        (listener) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
        () => state
    );
}

export function hasMore(home: HomeState): boolean {
    return home.maxDeliveryFee !== null && home.nextCursor !== null;
}

export function visibleFurnitures(home: HomeState): FurnitureSummary[] {
    if (home.selectedCategory === null) return home.furnitures;
    return home.furnitures.filter((furniture) => furniture.category === home.selectedCategory);
}

/** 불러온 판매 중 가구에서 제목에 [query]가 들어간 가구를 찾는다. 서버 검색 API가 없어 화면에서 거른다. */
export function searchFurnitures(home: HomeState, query: string): FurnitureSummary[] {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return [];
    return home.furnitures.filter((furniture) => furniture.title.toLowerCase().includes(keyword));
}

/** 다른 계정으로 로그인하거나 로그아웃하면 이전 목록을 버린다. */
export function resetHomeStore() {
    listingsRequest += 1;
    shownMaxDeliveryFee = null;
    state = initialState;
    listeners.forEach((listener) => listener());
}

/**
 * 홈에 들어올 때마다 부른다. 다른 화면에서 가구를 등록·수정·삭제하고 돌아와도
 * 보여주는 목록이 있으면 로딩 없이 최신 목록으로 바꾼다.
 * 배송비로 거른 목록은 첫 페이지만 다시 받으면 이어 받은 페이지가 사라지므로 그대로 둔다.
 */
export function enterHome(requireDong: () => void) {
    onRequireDong = requireDong;
    if (state.furnitures.length === 0) {
        loadListings();
    } else if (state.maxDeliveryFee === null) {
        fetchListings();
    }
    loadProfile();
}

/** 프로필은 부가 정보라 실패해도 알리지 않고 기본 아이콘을 유지한다. */
function loadProfile() {
    userRepository.getMe()
        .then((member) => update({ profileInitial: member.loginId.slice(0, 1).toUpperCase() }))
        .catch(() => undefined);
}

export function loadListings() {
    update({ isLoading: true, hasError: false });
    fetchListings();
}

export function refreshHome() {
    update({ isRefreshing: true });
    fetchListings();
    loadProfile();
}

export function selectCategory(category: FurnitureCategory | null) {
    update({ selectedCategory: category });
}

/** 배송비 예산을 바꾸고 첫 페이지부터 다시 받는다. null이면 필터를 해제한다. */
export function selectMaxDeliveryFee(maxDeliveryFee: number | null) {
    if (state.maxDeliveryFee === maxDeliveryFee) return;
    update({ maxDeliveryFee, isLoading: true, hasError: false });
    fetchListings();
}

/** 배송비로 거른 목록의 다음 페이지를 이어 받는다. */
export function loadMore() {
    const { maxDeliveryFee, nextCursor, isLoading, isRefreshing, isLoadingMore } = state;
    if (maxDeliveryFee === null || nextCursor === null) return;
    if (isLoading || isRefreshing || isLoadingMore) return;
    update({ isLoadingMore: true });
    const request = ++listingsRequest;
    furnitureRepository.getFurnitureWithinDeliveryFee(maxDeliveryFee, nextCursor)
        .then((page) => {
            if (request !== listingsRequest) return;
            update((current) => {
                // 페이지 사이에 새 매물이 끼어도 같은 매물이 두 번 보이지 않게 한다
                const seen = new Set(current.furnitures.map((furniture) => furniture.id));
                return {
                    furnitures: [...current.furnitures, ...page.items.filter((item) => !seen.has(item.id))],
                    nextCursor: page.nextCursor,
                    isLoadingMore: false
                };
            });
        })
        .catch(() => {
            if (request !== listingsRequest) return;
            update({ isLoadingMore: false });
            showSnackbar('가구를 더 불러오지 못했습니다');
        });
}

function fetchListings() {
    const request = ++listingsRequest;
    const maxDeliveryFee = state.maxDeliveryFee;
    const pageRequest: Promise<FurniturePage> = maxDeliveryFee === null
        ? furnitureRepository.getFurniture().then((items) => ({ items, nextCursor: null }))
        : furnitureRepository.getFurnitureWithinDeliveryFee(maxDeliveryFee, null);
    pageRequest
        .then((page) => {
            if (request !== listingsRequest) return;
            shownMaxDeliveryFee = maxDeliveryFee;
            update({
                furnitures: page.items,
                nextCursor: page.nextCursor,
                isLoading: false,
                isRefreshing: false,
                isLoadingMore: false,
                hasError: false
            });
        })
        .catch((error: unknown) => {
            if (request !== listingsRequest) return;
            if (isMemberDongRequired(error)) {
                update({ maxDeliveryFee: shownMaxDeliveryFee, isLoading: false, isRefreshing: false, isLoadingMore: false });
                onRequireDong();
                return;
            }
            const hasListings = state.furnitures.length > 0;
            update((current) => ({
                isLoading: false,
                isRefreshing: false,
                isLoadingMore: false,
                hasError: !hasListings,
                maxDeliveryFee: hasListings ? shownMaxDeliveryFee : current.maxDeliveryFee
            }));
            if (hasListings) showSnackbar('가구 목록을 새로고침하지 못했습니다');
        });
}
