import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { getPriceVariant } from '../../analytics/priceScenario';
import { tokenStore } from '../../api/http';
import {
    categoryLabel,
    conditionGrades,
    formatRegisteredDate,
    formatWon,
    PAYING_LABEL,
    selectableCategories,
    TRADING_LABEL,
    type ConditionGrade,
    type FurnitureCategory,
    type FurnitureSummary,
    type OrderState
} from '../../model/furniture';
import { digitsOnly } from '../../model/member';
import { loginStateFrom, routes, useNavigateBack, useNavigateToLogin, useNavigateToLoginFromHere } from '../../navigation';
import { refreshUnreadNotifications, useHasUnreadNotifications } from '../../push/unreadNotifications';
import {
    BottomBar,
    BottomSheet,
    Button,
    CenterFill,
    ChipRow,
    ExtendedFab,
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
    TextField,
    ToggleButtonGroup,
    TopAppBar,
    TrailingColumn
} from '../../ui/components';
import { Icon } from '../../ui/Icon';
import { shapeStyle } from '../../ui/shapes';
import { DeliveryFeeBudget } from './deliveryFeeBudget';
import { PriceFilter, type PriceRange } from './priceRange';
import {
    clearFilters,
    enterHome,
    hasMore,
    loadListings,
    loadMore,
    refreshHome,
    selectCategory,
    selectGrades,
    selectMaxDeliveryFee,
    selectPriceRange,
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

    const navigateToLogin = useNavigateToLogin();
    useEffect(() => {
        // 배송비 필터는 내 행정동이 있어야 하므로, 로그인 전이면 로그인부터 하게 한다
        enterHome(() => (tokenStore.get()
            ? navigate(routes.profileEdit)
            : navigate(routes.login, { state: loginStateFrom({ pathname: routes.home, search: '', state: null }) })),
        navigateToLogin);
    }, [navigate, navigateToLogin]);

    const openListing = (listingId: number) => navigate(routes.furnitureDetail(listingId));

    const isLoggedIn = Boolean(tokenStore.get());
    const [isFabExpanded, setIsFabExpanded] = useState(true);
    // 목록을 내리면 등록 버튼을 아이콘만 남기고 접는다
    useEffect(() => {
        const onScroll = () => setIsFabExpanded(window.scrollY < 120);
        window.addEventListener('scroll', onScroll, { passive: true });
        return () => window.removeEventListener('scroll', onScroll);
    }, []);

    return (
        <Scaffold
            // 로그인 전에는 가구 등록 대신 배송원 안내를 아래에 띄운다
            bottomBar={!isLoggedIn && <DriverBanner onClick={() => navigate(routes.driverLogin)} />}
            floatingActionButton={isLoggedIn && <ExtendedFab expanded={isFabExpanded} icon="add" label="가구 등록" onClick={() => navigate(routes.listingCreate)} />}
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

/** 로그인하지 않은 방문자에게 배송원 로그인을 권한다. */
function DriverBanner({ onClick }: { onClick: () => void }) {
    return (
        <BottomBar>
            <button className={styles['driver-banner']} onClick={onClick} type="button">
                <span className={styles['driver-banner__shape']} style={shapeStyle('cookie6')}>
                    <Icon name="localShipping" size={20} />
                </span>
                <span className={styles['driver-banner__lines']}>
                    <span className="type-title-medium-em">배송원이신가요?</span>
                    <span className="type-label-medium">가까운 가구 배송을 골라 가볍게 시작해보세요</span>
                </span>
                <Icon name="chevronRight" />
            </button>
        </BottomBar>
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
        <div className={styles.list}>
            <div className={styles['list-header']}>
                <ToggleButtonGroup
                    ariaLabel="카테고리"
                    labelOf={(category) => category === null ? '전체' : categoryLabel(category)}
                    onSelect={selectCategory}
                    options={[null, ...selectableCategories] as Array<FurnitureCategory | null>}
                    selected={home.selectedCategory}
                />
                <FilterBar home={home} />
            </div>
            {home.isFiltering ? <CenterFill><LoadingIndicator /></CenterFill> : (
                <>
                    {listings.length === 0 && !canLoadMore && <CenterFill><EmptyListMessage home={home} /></CenterFill>}
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

/** 조건에 맞는 가구가 없을 때의 안내. 배송비만 걸었을 때는 예산을 늘리라고 안내한다. */
function EmptyListMessage({ home }: { home: HomeState }) {
    const hasOtherFilter = home.selectedCategory !== null || home.selectedGrades.length > 0 || home.priceRange !== null;
    if (!hasOtherFilter && home.maxDeliveryFee === null) {
        return <SettyMessage description="곧 새로운 가구가 올라올 거예요." title="아직 등록된 가구가 없어요" />;
    }
    if (!hasOtherFilter) {
        return <SettyMessage description="예산을 늘리거나 필터를 해제해 보세요." title="배송비 예산 안의 가구가 없어요" />;
    }
    return <SettyMessage description="카테고리·등급·가격 필터를 바꿔 보세요." title="조건에 맞는 가구가 없어요" />;
}

/**
 * 필터 칩 줄. 등급·가격·배송비 칩은 누르면 시트가 열리고, 고른 값이 있으면 칩에 값을 보여 주며 강조된다.
 * 하나라도 걸려 있으면 맨 앞에 초기화 칩이 나온다.
 */
function FilterBar({ home }: { home: HomeState }) {
    const [openSheet, setOpenSheet] = useState<'grade' | 'price' | 'deliveryFee' | null>(null);
    // 배송비는 내 행정동으로 계산하므로 로그인 전이면 시트 대신 로그인으로 보낸다. 로그인하면 홈으로 돌아온다
    const navigateToLoginFromHere = useNavigateToLoginFromHere();
    const openDeliveryFeeSheet = () => (tokenStore.get() ? setOpenSheet('deliveryFee') : navigateToLoginFromHere());
    const hasFilter = home.selectedGrades.length > 0 || home.priceRange !== null || home.maxDeliveryFee !== null;
    const close = () => setOpenSheet(null);
    return (
        <>
            <ChipRow>
                {hasFilter && <FilterChip ariaLabel="필터 초기화" label="" leadingIcon="refresh" onClick={clearFilters} selected={false} />}
                <FilterChip
                    label={home.selectedGrades.length > 0 ? `${home.selectedGrades.join('·')}급` : '등급'}
                    onClick={() => setOpenSheet('grade')}
                    selected={home.selectedGrades.length > 0}
                    trailingIcon="expandMore"
                />
                <FilterChip
                    label={home.priceRange !== null ? PriceFilter.label(home.priceRange) : '가격'}
                    onClick={() => setOpenSheet('price')}
                    selected={home.priceRange !== null}
                    trailingIcon="expandMore"
                />
                <FilterChip
                    label={home.maxDeliveryFee !== null ? `배송비 ${DeliveryFeeBudget.format(home.maxDeliveryFee)} 이하` : '배송비'}
                    onClick={openDeliveryFeeSheet}
                    selected={home.maxDeliveryFee !== null}
                    trailingIcon="expandMore"
                />
            </ChipRow>
            {openSheet === 'grade' && (
                <GradeSheet initialGrades={home.selectedGrades} onDismiss={close} onSelect={(grades) => { close(); selectGrades(grades); }} />
            )}
            {openSheet === 'price' && (
                <PriceRangeSheet initialRange={home.priceRange} onDismiss={close} onSelect={(range) => { close(); selectPriceRange(range); }} />
            )}
            {openSheet === 'deliveryFee' && (
                <DeliveryFeeBudgetSheet
                    initialBudget={home.maxDeliveryFee ?? DeliveryFeeBudget.DEFAULT}
                    onDismiss={close}
                    onSelect={(budget) => { close(); selectMaxDeliveryFee(budget); }}
                />
            )}
        </>
    );
}

/** 상태등급을 여러 개 고르는 시트. 아무것도 안 고르고 적용하면 필터를 푼다. */
function GradeSheet({ initialGrades, onDismiss, onSelect }: { initialGrades: readonly ConditionGrade[]; onDismiss: () => void; onSelect: (grades: readonly ConditionGrade[]) => void }) {
    const [grades, setGrades] = useState<readonly ConditionGrade[]>(initialGrades);
    const toggle = (grade: ConditionGrade) => {
        setGrades((current) => current.includes(grade) ? current.filter((item) => item !== grade) : [...current, grade]);
    };
    return (
        <BottomSheet label="상태등급" onDismiss={onDismiss}>
            <div className={screenStyles['sheet-padding']}>
                <h2 className="type-title-large-em" style={{ margin: 0 }}>상태등급</h2>
                <p className="type-body-medium-em" style={{ margin: '4px 0 16px', color: 'var(--on-surface-variant)' }}>
                    여러 등급을 같이 고를 수 있어요.
                </p>
                <ChipRow>
                    {conditionGrades.map((grade) => (
                        <FilterChip key={grade} label={`${grade}급`} onClick={() => toggle(grade)} selected={grades.includes(grade)} />
                    ))}
                </ChipRow>
                <div className={styles['sheet-actions']}>
                    <Button onClick={() => onSelect([])} size="medium" variant="outlined">필터 해제</Button>
                    <Button onClick={() => onSelect(grades)} size="medium">적용</Button>
                </div>
            </div>
        </BottomSheet>
    );
}

/** 물건 가격 범위를 고르는 시트. 상한 칩으로 바로 고르거나 최소·최대를 직접 넣는다. [onSelect]에 null을 넘기면 필터를 해제한다. */
function PriceRangeSheet({ initialRange, onDismiss, onSelect }: { initialRange: PriceRange | null; onDismiss: () => void; onSelect: (range: PriceRange | null) => void }) {
    const [min, setMin] = useState(initialRange?.min === null || initialRange === null ? '' : String(initialRange.min));
    const [max, setMax] = useState(initialRange?.max === null || initialRange === null ? '' : String(initialRange.max));
    const range: PriceRange = { min: min === '' ? null : Number(min), max: max === '' ? null : Number(max) };
    const tooLarge = (range.min ?? 0) > PriceFilter.MAX || (range.max ?? 0) > PriceFilter.MAX;
    const orderError = range.min !== null && range.max !== null && range.min > range.max;
    const canApply = !tooLarge && !orderError && (range.min !== null || range.max !== null);
    return (
        <BottomSheet label="물건 가격" onDismiss={onDismiss}>
            <div className={screenStyles['sheet-padding']}>
                <h2 className="type-title-large-em" style={{ margin: 0 }}>물건 가격</h2>
                <p className="type-body-medium-em" style={{ margin: '4px 0 16px', color: 'var(--on-surface-variant)' }}>
                    배송비를 뺀 가격이에요. 한쪽만 넣어도 돼요.
                </p>
                <ChipRow style={{ marginBottom: 16 }}>
                    {PriceFilter.PRESETS.map((preset) => {
                        const isSelected = range.min === null && range.max === preset;
                        return (
                            <FilterChip
                                key={preset}
                                label={PriceFilter.label({ min: null, max: preset })}
                                onClick={() => { setMin(''); setMax(isSelected ? '' : String(preset)); }}
                                selected={isSelected}
                            />
                        );
                    })}
                </ChipRow>
                <div style={{ display: 'flex', gap: 8 }}>
                    <TextField
                        inputMode="numeric"
                        isError={orderError || (range.min ?? 0) > PriceFilter.MAX}
                        label="최소"
                        onChange={(value) => setMin(digitsOnly(value, PriceFilter.MAX_DIGITS))}
                        suffix="원"
                        value={min}
                    />
                    <TextField
                        inputMode="numeric"
                        isError={orderError || (range.max ?? 0) > PriceFilter.MAX}
                        label="최대"
                        onChange={(value) => setMax(digitsOnly(value, PriceFilter.MAX_DIGITS))}
                        suffix="원"
                        value={max}
                    />
                </div>
                <p className="type-label-medium" style={{ margin: '8px 0 0', color: orderError ? 'var(--error)' : 'var(--on-surface-variant)' }}>
                    {orderError ? '최소가 최대보다 커요' : '1억 원까지'}
                </p>
                <div className={styles['sheet-actions']}>
                    <Button onClick={() => onSelect(null)} size="medium" variant="outlined">필터 해제</Button>
                    <Button disabled={!canApply} onClick={() => onSelect(range)} size="medium">적용</Button>
                </div>
            </div>
        </BottomSheet>
    );
}

/** 슬라이더로 배송비 예산을 고르는 시트. [onSelect]에 null을 넘기면 필터를 해제한다. */
function DeliveryFeeBudgetSheet({ initialBudget, onDismiss, onSelect }: { initialBudget: number; onDismiss: () => void; onSelect: (budget: number | null) => void }) {
    const [budget, setBudget] = useState(Math.min(DeliveryFeeBudget.MAX, Math.max(DeliveryFeeBudget.MIN, initialBudget)));
    const progress = (budget - DeliveryFeeBudget.MIN) / (DeliveryFeeBudget.MAX - DeliveryFeeBudget.MIN);
    return (
        <BottomSheet label="배송비 예산" onDismiss={onDismiss}>
            <div className={screenStyles['sheet-padding']}>
                <h2 className="type-title-large-em" style={{ margin: 0 }}>배송비 예산</h2>
                <p className="type-body-medium-em" style={{ margin: '4px 0 0', color: 'var(--on-surface-variant)' }}>
                    내 행정동까지 이 금액 안으로 배송되는 가구만 보여드려요.
                </p>
                <p className={`${styles['budget-amount']} type-display-small-em`}>{DeliveryFeeBudget.format(budget)} 이하</p>
                {/* 기본 슬라이더는 투명하게 겹쳐 조작만 맡기고, 트랙과 손잡이는 직접 그린다 */}
                <div
                    className={styles.slider}
                    style={{ '--slider-progress': progress } as CSSProperties}
                >
                    <input
                        aria-label="배송비 예산"
                        className={styles.slider__input}
                        max={DeliveryFeeBudget.MAX}
                        min={DeliveryFeeBudget.MIN}
                        onChange={(event) => setBudget(Number(event.target.value))}
                        step={DeliveryFeeBudget.STEP}
                        type="range"
                        value={budget}
                    />
                    {progress > 0 && <span aria-hidden className={styles['slider__track--active']} />}
                    {/* 손잡이가 끝에 닿으면 남은 트랙이 없고, 끝에 가까우면 끝 점이 손잡이와 겹치므로 숨긴다 */}
                    {progress < 1 && (
                        <span aria-hidden className={styles['slider__track--inactive']}>
                            {progress < 0.9 && <span className={styles['slider__stop']} />}
                        </span>
                    )}
                    <span aria-hidden className={styles.slider__handle} />
                </div>
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
    const search = useListingSearch(query, home.selectedCategory, home.selectedGrades, home.priceRange, home.maxDeliveryFee);
    const results = search.items;
    const scrollRef = useRef<HTMLDivElement | null>(null);
    const triggerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        scrollRef.current?.scrollTo({ top: 0 });
    }, [query.trim(), home.selectedCategory, home.selectedGrades, home.priceRange, home.maxDeliveryFee]);

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
                <div className={styles['search-field']}>
                    <IconButton icon="arrowBack" iconSize={20} label="검색 닫기" onClick={navigateBack} />
                    <input
                        aria-label="중고 가구 검색"
                        autoFocus
                        className={styles['search-field__input']}
                        enterKeyHint="search"
                        maxLength={100}
                        onChange={(event) => setQuery(event.target.value)}
                        placeholder="중고 가구 검색"
                        type="search"
                        value={query}
                    />
                    {query && <IconButton icon="close" iconSize={20} label="검색어 지우기" onClick={() => setQuery('')} />}
                </div>
            </div>
            {!query.trim() && (
                <CenterFill>
                    <SettyMessage description="판매 중인 가구의 제목·설명·카테고리로 찾아드려요." title="어떤 가구를 찾으세요?" />
                </CenterFill>
            )}
            {query.trim() && !search.loading && !search.error && results.length === 0 && (
                <CenterFill>
                    <SettyMessage description="다른 단어로 다시 찾아보세요." title="검색 결과가 없어요" />
                </CenterFill>
            )}
            {search.loading && results.length === 0 && <CenterFill><LoadingIndicator /></CenterFill>}
            {search.error && results.length === 0 && (
                <CenterFill>
                    <SettyMessage action={<Button onClick={search.retry}>다시 시도</Button>} title="검색 결과를 불러오지 못했어요" />
                </CenterFill>
            )}
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
