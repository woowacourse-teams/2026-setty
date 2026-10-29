import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { getPriceVariant } from '../../analytics/priceScenario';
import { tokenStore } from '../../api/http';
import {
    categoryLabel,
    formatRegisteredDate,
    formatWon,
    PAYING_LABEL,
    selectableCategories,
    TRADING_LABEL,
    type FurnitureCategory,
    type FurnitureSummary,
    type OrderState
} from '../../model/furniture';
import { loginStateFrom, routes, useNavigateBack } from '../../navigation';
import { refreshUnreadNotifications, useHasUnreadNotifications } from '../../push/unreadNotifications';
import {
    BottomSheet,
    Button,
    CenterFill,
    ChipRow,
    FavoriteCount,
    FilterChip,
    FurnitureThumbnail,
    IconButton,
    LoadingIndicator,
    PullToRefresh,
    Scaffold,
    SegmentedList,
    SegmentedListItem,
    SettyMessage,
    ToggleButtonGroup,
    TopAppBar,
    TrailingColumn
} from '../../ui/components';
import { Icon } from '../../ui/Icon';
import { shapeStyle } from '../../ui/shapes';
import { DeliveryFeeBudget } from './deliveryFeeBudget';
import {
    enterHome,
    hasMore,
    loadListings,
    loadMore,
    refreshHome,
    selectCategory,
    selectMaxDeliveryFee,
    useHomeState,
    visibleFurnitures,
    type HomeState
} from './homeStore';
import screenStyles from '../screens.module.css';
import styles from './home.module.css';
import { useListingSearch } from './useListingSearch';

const SEARCH_PARAM = 'search';

export function HomeScreen() {
    const navigate = useNavigate();
    const home = useHomeState();
    const [searchParams] = useSearchParams();
    const isSearchOpen = searchParams.has(SEARCH_PARAM);

    useEffect(() => {
        // 배송비 필터는 내 행정동이 있어야 하므로, 로그인 전이면 로그인부터 하게 한다
        enterHome(() => (tokenStore.get()
            ? navigate(routes.profileEdit)
            : navigate(routes.login, { state: loginStateFrom({ pathname: routes.home, search: '', state: null }) })));
    }, [navigate]);

    const openListing = (listingId: number) => navigate(routes.furnitureDetail(listingId));

    return (
        <Scaffold
            topBar={(
                <TopAppBar
                    actions={(
                        <>
                            {/* 검색창은 기록에 남겨 브라우저 뒤로 가기로도 닫히게 한다 */}
                            <IconButton icon="search" label="가구 검색" onClick={() => navigate(`?${SEARCH_PARAM}=1`)} />
                            <NotificationsButton onClick={() => navigate(routes.notifications)} />
                            <ProfileButton onClick={() => navigate(routes.myPage)} profileInitial={home.profileInitial} />
                        </>
                    )}
                    height={72}
                    subtitle="무거운 중고 가구를 더 가볍게"
                    title={<span className={styles.brand}>SETTY</span>}
                />
            )}
        >
            <PullToRefresh enabled={!home.isLoading} isRefreshing={home.isRefreshing} onRefresh={refreshHome}>
                {home.isLoading && <CenterFill><LoadingIndicator /></CenterFill>}
                {!home.isLoading && home.hasError && (
                    <CenterFill>
                        <SettyMessage
                            action={<Button onClick={loadListings}>다시 시도</Button>}
                            description="네트워크 상태를 확인한 뒤 다시 시도해주세요."
                            title="가구 목록을 불러오지 못했어요"
                        />
                    </CenterFill>
                )}
                {!home.isLoading && !home.hasError && <ListingList home={home} onListingClick={openListing} />}
            </PullToRefresh>
            {isSearchOpen && (
                <SearchLayer
                    home={home}
                    onListingClick={(listingId) => {
                        // 상세에서 돌아왔을 때 검색창이 덮고 있지 않도록 검색 기록을 상세로 바꾼다
                        navigate(routes.furnitureDetail(listingId), { replace: true });
                    }}
                />
            )}
        </Scaffold>
    );
}

/** 로그인한 회원 아이디의 첫 글자를 Expressive 도형 안에 보여준다. 아직 모르면 계정 아이콘을 보여준다. */
/** 안 읽은 알림이 있으면 종 위에 점을 띄운다. 홈에 들어오거나 탭으로 돌아올 때 다시 확인한다. */
function NotificationsButton({ onClick }: { onClick: () => void }) {
    const hasUnread = useHasUnreadNotifications();

    useEffect(() => {
        void refreshUnreadNotifications();
        const refreshWhenVisible = () => {
            if (document.visibilityState === 'visible') void refreshUnreadNotifications();
        };
        document.addEventListener('visibilitychange', refreshWhenVisible);
        return () => document.removeEventListener('visibilitychange', refreshWhenVisible);
    }, []);

    return (
        <span className={styles['notifications-button']}>
            <IconButton icon="notifications" label={hasUnread ? '알림함, 새 알림 있음' : '알림함'} onClick={onClick} />
            {hasUnread && <span aria-hidden className={styles['unread-dot']} />}
        </span>
    );
}

function ProfileButton({ profileInitial, onClick }: { profileInitial: string | null; onClick: () => void }) {
    return (
        <button aria-label="내 정보" className={styles['profile-button']} onClick={onClick} type="button">
            <span className={styles['profile-button__shape']} style={shapeStyle('cookie9')}>
                {profileInitial ?? <Icon name="accountCircle" size={20} />}
            </span>
        </button>
    );
}

// 마지막에서 이만큼 앞의 항목이 보이면 다음 페이지를 받는다. 목록 항목 한 줄의 대략적인 높이로 거리를 잡는다
const LOAD_MORE_THRESHOLD = 3;
const LISTING_ITEM_HEIGHT = 100;

function ListingList({ home, onListingClick }: { home: HomeState; onListingClick: (listingId: number) => void }) {
    const listings = visibleFurnitures(home);
    const canLoadMore = hasMore(home);
    const triggerRef = useRef<HTMLDivElement | null>(null);
    // 받은 뒤에도 끝이 보이면 항목 수가 바뀌었으므로 다시 받고, 실패해 항목 수가 그대로면 다시 받지 않는다
    const requestedCount = useRef<number | null>(null);
    const itemCount = home.furnitures.length;

    useEffect(() => {
        const trigger = triggerRef.current;
        if (!canLoadMore || !trigger || home.isFiltering || home.isRefreshing || home.isLoadingMore) return;
        const observer = new IntersectionObserver(([entry]) => {
            if (!entry.isIntersecting || requestedCount.current === itemCount) return;
            requestedCount.current = itemCount;
            loadMore();
        }, { rootMargin: `0px 0px ${LOAD_MORE_THRESHOLD * LISTING_ITEM_HEIGHT}px 0px` });
        observer.observe(trigger);
        return () => observer.disconnect();
    }, [canLoadMore, itemCount, home.isFiltering, home.isRefreshing, home.isLoadingMore]);

    return (
        <div className={home.isFiltering ? `${styles.list} ${styles['list--filtering']}` : styles.list}>
            <div className={styles['list-header']}>
                <ToggleButtonGroup
                    ariaLabel="카테고리"
                    labelOf={(category) => category === null ? '전체' : categoryLabel(category)}
                    onSelect={selectCategory}
                    options={[null, ...selectableCategories] as Array<FurnitureCategory | null>}
                    selected={home.selectedCategory}
                />
                <DeliveryFeeFilter maxDeliveryFee={home.maxDeliveryFee} />
            </div>
            {home.isFiltering ? <CenterFill><LoadingIndicator /></CenterFill> : (
                <>
                    {listings.length === 0 && !canLoadMore && (
                        home.maxDeliveryFee === null ? (
                            <SettyMessage description="곧 새로운 가구가 올라올 거예요." style={{ marginTop: 48 }} title="아직 등록된 가구가 없어요" />
                        ) : (
                            <SettyMessage description="예산을 늘리거나 필터를 해제해 보세요." style={{ marginTop: 48 }} title="배송비 예산 안의 가구가 없어요" />
                        )
                    )}
                    <SegmentedList>
                        {listings.map((listing, index) => (
                            <ListingItem
                                count={listings.length}
                                furniture={listing}
                                index={index}
                                key={listing.id}
                                onClick={() => onListingClick(listing.id)}
                            />
                        ))}
                    </SegmentedList>
                    {canLoadMore && (
                        <div className={styles['load-more']} ref={triggerRef}>
                            {home.isLoadingMore ? <LoadingIndicator /> : <Button onClick={loadMore} variant="text">더 보기</Button>}
                        </div>
                    )}
                </>
            )}
        </div>
    );
}

function orderLabelOf(orderState: OrderState): string | null {
    if (orderState === 'TRADING') return TRADING_LABEL;
    if (orderState === 'PAYING') return PAYING_LABEL;
    return null;
}

function ListingItem({ furniture, index, count, onClick }: { furniture: FurnitureSummary; index: number; count: number; onClick: () => void }) {
    const priceVariant = getPriceVariant();
    const showDeliveryCosts = priceVariant === 'B';
    const orderLabel = orderLabelOf(furniture.orderState);
    return (
        <SegmentedListItem
            count={count}
            headline={<span className="type-title-medium">{furniture.title}</span>}
            index={index}
            leading={(
                <FurnitureThumbnail
                    alt={furniture.title}
                    imageUrl={furniture.thumbnailUrl}
                    placeholder={categoryLabel(furniture.category)}
                />
            )}
            onClick={onClick}
            overline={[categoryLabel(furniture.category), `${furniture.conditionGrade}급`, furniture.dongName].filter(Boolean).join(' · ')}
            supporting={(
                <div data-price-variant={priceVariant}>
                    <div className={`${screenStyles['price-primary']} type-body-large-em`}>
                        {formatWon(showDeliveryCosts ? furniture.totalPrice : furniture.price)}
                    </div>
                    {showDeliveryCosts && (
                        <div className="type-label-medium">매물 {formatWon(furniture.price)} · 배송비 {formatWon(furniture.deliveryFee)} 포함</div>
                    )}
                    {orderLabel && <div className={`${screenStyles['tertiary-text']} type-label-medium`}>{orderLabel}</div>}
                </div>
            )}
            trailing={(
                <TrailingColumn>
                    <span className="type-label-medium">{formatRegisteredDate(furniture.createdAt)}</span>
                    <FavoriteCount count={furniture.favoriteCount} />
                </TrailingColumn>
            )}
        />
    );
}

/**
 * 배송비 예산 칩. "배송비 전체"는 필터를 해제하고, 정해 둔 예산 칩은 다시 누르면 해제한다.
 * 정해 둔 예산에 없는 값은 직접 입력해 고르고, 고른 값은 직접 입력 칩에 보여준다.
 */
function DeliveryFeeFilter({ maxDeliveryFee }: { maxDeliveryFee: number | null }) {
    const [isBudgetSheetVisible, setIsBudgetSheetVisible] = useState(false);
    const customBudget = maxDeliveryFee !== null && !DeliveryFeeBudget.PRESETS.includes(maxDeliveryFee) ? maxDeliveryFee : null;
    return (
        <>
            <ChipRow>
                <FilterChip label="배송비 전체" onClick={() => selectMaxDeliveryFee(null)} selected={maxDeliveryFee === null} />
                {DeliveryFeeBudget.PRESETS.map((budget) => {
                    const isSelected = maxDeliveryFee === budget;
                    return (
                        <FilterChip
                            key={budget}
                            label={`${DeliveryFeeBudget.format(budget)} 이하`}
                            onClick={() => selectMaxDeliveryFee(isSelected ? null : budget)}
                            selected={isSelected}
                        />
                    );
                })}
                <FilterChip
                    label={customBudget !== null ? `${DeliveryFeeBudget.format(customBudget)} 이하` : '직접 설정'}
                    leadingIcon="edit"
                    onClick={() => setIsBudgetSheetVisible(true)}
                    selected={customBudget !== null}
                />
            </ChipRow>
            {isBudgetSheetVisible && (
                <DeliveryFeeBudgetSheet
                    initialBudget={maxDeliveryFee ?? DeliveryFeeBudget.DEFAULT}
                    onDismiss={() => setIsBudgetSheetVisible(false)}
                    onSelect={(budget) => {
                        setIsBudgetSheetVisible(false);
                        selectMaxDeliveryFee(budget);
                    }}
                />
            )}
        </>
    );
}

/** 슬라이더로 배송비 예산을 고르는 시트. [onSelect]에 null을 넘기면 필터를 해제한다. */
function DeliveryFeeBudgetSheet({ initialBudget, onDismiss, onSelect }: { initialBudget: number; onDismiss: () => void; onSelect: (budget: number | null) => void }) {
    const [budget, setBudget] = useState(Math.min(DeliveryFeeBudget.MAX, Math.max(DeliveryFeeBudget.MIN, initialBudget)));
    return (
        <BottomSheet label="배송비 예산" onDismiss={onDismiss}>
            <div className={screenStyles['sheet-padding']}>
                <h2 className="type-title-large-em" style={{ margin: 0 }}>배송비 예산</h2>
                <p className="type-body-medium-em" style={{ margin: '4px 0 0', color: 'var(--on-surface-variant)' }}>
                    내 행정동까지 이 금액 안으로 배송되는 가구만 보여드려요.
                </p>
                <p className={`${styles['budget-amount']} type-display-small-em`}>{DeliveryFeeBudget.format(budget)} 이하</p>
                <input
                    aria-label="배송비 예산"
                    className={styles.slider}
                    max={DeliveryFeeBudget.MAX}
                    min={DeliveryFeeBudget.MIN}
                    onChange={(event) => setBudget(Number(event.target.value))}
                    step={DeliveryFeeBudget.STEP}
                    type="range"
                    value={budget}
                />
                <div className={`${styles['slider-range']} type-label-medium`}>
                    <span>{DeliveryFeeBudget.format(DeliveryFeeBudget.MIN)}</span>
                    <span>{DeliveryFeeBudget.format(DeliveryFeeBudget.MAX)}</span>
                </div>
                <div className={styles['sheet-actions']}>
                    <Button onClick={() => onSelect(null)} size="medium" variant="outlined">필터 해제</Button>
                    <Button onClick={() => onSelect(budget)} size="medium">적용</Button>
                </div>
            </div>
        </BottomSheet>
    );
}

function SearchLayer({ home, onListingClick }: { home: HomeState; onListingClick: (listingId: number) => void }) {
    const navigateBack = useNavigateBack();
    const [query, setQuery] = useState('');
    const search = useListingSearch(query, home.selectedCategory, home.maxDeliveryFee);
    const results = search.items;
    const scrollRef = useRef<HTMLDivElement | null>(null);
    const triggerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        scrollRef.current?.scrollTo({ top: 0 });
    }, [query.trim(), home.selectedCategory, home.maxDeliveryFee]);

    useEffect(() => {
        const trigger = triggerRef.current;
        if (!trigger || !search.nextCursor || search.loading || search.error) return;
        const observer = new IntersectionObserver(([entry]) => {
            if (entry.isIntersecting) search.loadMore();
        }, {
            root: scrollRef.current,
            rootMargin: `0px 0px ${LOAD_MORE_THRESHOLD * LISTING_ITEM_HEIGHT}px 0px`
        });
        observer.observe(trigger);
        return () => observer.disconnect();
    }, [search.nextCursor, search.loading, search.error, search.loadMore]);

    return (
        <div className={styles['search-layer']} ref={scrollRef}>
            <div className={styles['search-bar']}>
                <IconButton icon="arrowBack" label="검색 닫기" onClick={navigateBack} />
                <input
                    aria-label="중고 가구 검색"
                    autoFocus
                    className={styles['search-bar__input']}
                    enterKeyHint="search"
                    maxLength={100}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="중고 가구 검색"
                    type="search"
                    value={query}
                />
            </div>
            {!query.trim() && (
                <SettyMessage description="판매 중인 가구의 제목으로 찾아드려요." style={{ marginTop: 48 }} title="어떤 가구를 찾으세요?" />
            )}
            {query.trim() && !search.loading && !search.error && results.length === 0 && (
                <SettyMessage description="다른 단어로 다시 찾아보세요." style={{ marginTop: 48 }} title="검색 결과가 없어요" />
            )}
            {search.loading && results.length === 0 && <LoadingIndicator />}
            {search.error && results.length === 0 && <SettyMessage title="검색 결과를 불러오지 못했어요"
                action={<Button onClick={search.retry}>다시 시도</Button>} />}
            {results.length > 0 && (
                <div aria-label="검색 결과" className={styles['search-results']} role="region">
                    <SegmentedList>
                        {results.map((listing, index) => (
                            <ListingItem
                                count={results.length}
                                furniture={listing}
                                index={index}
                                key={listing.id}
                                onClick={() => onListingClick(listing.id)}
                            />
                        ))}
                    </SegmentedList>
                    {search.nextCursor && (
                        <div className={styles['load-more']} ref={triggerRef}>
                            {search.loading && <LoadingIndicator />}
                            {search.error && <SettyMessage title="가구를 더 불러오지 못했어요"
                                action={<Button onClick={search.loadMore}>다시 시도</Button>} />}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
