import { useSyncExternalStore } from 'react';
import { isMemberDongRequired } from '../../api/errors';
import { tokenStore } from '../../api/http';
import { furnitureRepository, userRepository } from '../../api/repositories';
import { conditionGrades, type ConditionGrade, type FurnitureCategory, type FurniturePage, type FurnitureSummary } from '../../model/furniture';
import { showSnackbar } from '../../ui/snackbar';
import { PriceFilter, type PriceRange } from './priceRange';

export type HomeState = {
    furnitures: FurnitureSummary[];
    selectedCategory: FurnitureCategory | null;
    // 고른 상태등급. 비어 있으면 등급 조건 없이 받는다
    selectedGrades: readonly ConditionGrade[];
    // 고른 물건 가격 범위. null이면 가격 조건 없이 받는다
    priceRange: PriceRange | null;
    // 고른 배송비 예산. null이면 배송비 조건 없이 페이지를 받는다
    maxDeliveryFee: number | null;
    // 이어 받을 다음 페이지 커서. 마지막 페이지면 null
    nextCursor: string | null;
    isLoadingMore: boolean;
    // 프로필 버튼에 보여줄 아이디 첫 글자. 회원 정보를 아직 못 받았으면 null
    profileInitial: string | null;
    isLoading: boolean;
    // 필터를 바꿔 첫 페이지를 다시 받는 중. 필터 줄은 두고 목록 자리에만 로딩을 보여준다
    isFiltering: boolean;
    isRefreshing: boolean;
    hasError: boolean;
};

const initialState: HomeState = {
    furnitures: [],
    selectedCategory: null,
    selectedGrades: [],
    priceRange: null,
    maxDeliveryFee: null,
    nextCursor: null,
    isLoadingMore: false,
    profileInitial: null,
    isLoading: true,
    isFiltering: false,
    isRefreshing: false,
    hasError: false
};

let state: HomeState = initialState;
// 지금 보여주는 목록을 받은 배송비 예산. 다른 예산으로 받다 실패하면 필터를 이 값으로 되돌린다
let shownMaxDeliveryFee: number | null = null;
let shownCategory: FurnitureCategory | null = null;
let shownGrades: readonly ConditionGrade[] = [];
let shownPriceRange: PriceRange | null = null;
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
    return home.nextCursor !== null;
}

export function visibleFurnitures(home: HomeState): FurnitureSummary[] {
    return home.furnitures;
}

/** 다른 계정으로 로그인하거나 로그아웃하면 이전 목록을 버린다. */
export function resetHomeStore() {
    listingsRequest += 1;
    shownMaxDeliveryFee = null;
    shownCategory = null;
    shownGrades = [];
    shownPriceRange = null;
    state = initialState;
    listeners.forEach((listener) => listener());
}

/**
 * 홈에 들어올 때마다 부른다. 다른 화면에서 가구를 등록·수정·삭제하고 돌아와도
 * 보여주는 목록이 있으면 로딩 없이 최신 목록으로 바꾼다.
 * 복귀 시에는 이미 읽은 범위만 페이지로 다시 읽어 스크롤 위치와 최신 상태를 유지한다.
 */
export function enterHome(requireDong: () => void) {
    onRequireDong = requireDong;
    if (state.furnitures.length === 0) {
        loadListings();
    } else {
        fetchListings(state.furnitures.length);
    }
    loadProfile();
}

/** 프로필은 부가 정보라 실패해도 알리지 않고 기본 아이콘을 유지한다. 로그인 전이면 묻지 않는다. */
function loadProfile() {
    if (!tokenStore.get()) return;
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

/**
 * 필터를 바꾸면 필터 줄은 그대로 둔 채 목록 자리에만 로딩을 보여줘 화면이 깜빡이지 않게 한다.
 * 아직 보여주는 목록이 없을 때만 전체 로딩을 보여준다.
 */
function changeFilter(patch: Pick<Partial<HomeState>, 'selectedCategory' | 'selectedGrades' | 'priceRange' | 'maxDeliveryFee'>) {
    const isListShown = !state.isLoading && !state.hasError;
    update(isListShown ? { ...patch, isFiltering: true } : { ...patch, isLoading: true, hasError: false });
    fetchListings();
}

export function selectCategory(category: FurnitureCategory | null) {
    if (state.selectedCategory === category) return;
    changeFilter({ selectedCategory: category });
}

/** 고른 등급을 한 번에 바꾼다. 좋은 등급부터 보내고, 같은 묶음이면 다시 받지 않는다. */
export function selectGrades(grades: readonly ConditionGrade[]) {
    const selected = conditionGrades.filter((grade) => grades.includes(grade));
    if (selected.join() === state.selectedGrades.join()) return;
    changeFilter({ selectedGrades: selected });
}

/** 등급·가격·배송비 조건을 한 번에 푼다. 카테고리는 필터가 아니라 탐색이라 그대로 둔다. */
export function clearFilters() {
    if (state.selectedGrades.length === 0 && state.priceRange === null && state.maxDeliveryFee === null) return;
    changeFilter({ selectedGrades: [], priceRange: null, maxDeliveryFee: null });
}

/** 물건 가격 범위를 바꾸고 첫 페이지부터 다시 받는다. null이면 필터를 해제한다. */
export function selectPriceRange(priceRange: PriceRange | null) {
    if (PriceFilter.equals(state.priceRange, priceRange)) return;
    changeFilter({ priceRange });
}

/** 배송비 예산을 바꾸고 첫 페이지부터 다시 받는다. null이면 필터를 해제한다. */
export function selectMaxDeliveryFee(maxDeliveryFee: number | null) {
    if (state.maxDeliveryFee === maxDeliveryFee) return;
    changeFilter({ maxDeliveryFee });
}

/** 현재 검색 조건의 다음 페이지를 이어 받는다. */
export function loadMore() {
    const { nextCursor, isLoading, isFiltering, isRefreshing, isLoadingMore } = state;
    if (nextCursor === null) return;
    if (isLoading || isFiltering || isRefreshing || isLoadingMore) return;
    update({ isLoadingMore: true });
    const request = ++listingsRequest;
    furnitureRepository.getFurniturePage({ ...filterOf(state), cursor: nextCursor })
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

/** 현재 고른 필터를 목록 요청 조건으로 바꾼다. */
function filterOf({ selectedCategory, selectedGrades, priceRange, maxDeliveryFee }: HomeState) {
    return {
        category: selectedCategory,
        conditionGrades: selectedGrades,
        minPrice: priceRange?.min ?? null,
        maxPrice: priceRange?.max ?? null,
        maxDeliveryFee
    };
}

function fetchListings(retainCount = 0) {
    if (retainCount > 0) update({ isRefreshing: true });
    const request = ++listingsRequest;
    const { maxDeliveryFee, selectedCategory, selectedGrades, priceRange } = state;
    const filter = filterOf(state);
    const pageRequest = (async (): Promise<FurniturePage> => {
        const page = await furnitureRepository.getFurniturePage(filter);
        while (request === listingsRequest && page.nextCursor !== null && page.items.length < retainCount) {
            const next = await furnitureRepository.getFurniturePage({ ...filter, cursor: page.nextCursor });
            const seen = new Set(page.items.map((item) => item.id));
            page.items.push(...next.items.filter((item) => !seen.has(item.id)));
            page.nextCursor = next.nextCursor;
        }
        return page;
    })();
    pageRequest
        .then((page) => {
            if (request !== listingsRequest) return;
            shownMaxDeliveryFee = maxDeliveryFee;
            shownCategory = selectedCategory;
            shownGrades = selectedGrades;
            shownPriceRange = priceRange;
            update({
                furnitures: page.items,
                nextCursor: page.nextCursor,
                isLoading: false,
                isFiltering: false,
                isRefreshing: false,
                isLoadingMore: false,
                hasError: false
            });
        })
        .catch((error: unknown) => {
            if (request !== listingsRequest) return;
            if (isMemberDongRequired(error)) {
                update({ selectedCategory: shownCategory, selectedGrades: shownGrades, priceRange: shownPriceRange, maxDeliveryFee: shownMaxDeliveryFee, isLoading: false, isFiltering: false, isRefreshing: false, isLoadingMore: false });
                onRequireDong();
                return;
            }
            const hasListings = state.furnitures.length > 0;
            update((current) => ({
                isLoading: false,
                isFiltering: false,
                isRefreshing: false,
                isLoadingMore: false,
                hasError: !hasListings,
                maxDeliveryFee: hasListings ? shownMaxDeliveryFee : current.maxDeliveryFee,
                selectedCategory: hasListings ? shownCategory : current.selectedCategory,
                selectedGrades: hasListings ? shownGrades : current.selectedGrades,
                priceRange: hasListings ? shownPriceRange : current.priceRange
            }));
            if (hasListings) showSnackbar('가구 목록을 새로고침하지 못했습니다');
        });
}
