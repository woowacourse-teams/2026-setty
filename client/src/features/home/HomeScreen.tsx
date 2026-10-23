import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import {
    categoryLabel,
    formatRegisteredDate,
    formatWon,
    PAYMENT_IN_PROGRESS_LABEL,
    selectableCategories,
    type FurnitureCategory,
    type FurnitureSummary
} from '../../model/furniture';
import { routes, useNavigateBack } from '../../navigation';
import {
    BottomSheet,
    Button,
    CenterFill,
    ChipRow,
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
    TopAppBar
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
    searchFurnitures,
    selectCategory,
    selectMaxDeliveryFee,
    useHomeState,
    visibleFurnitures,
    type HomeState
} from './homeStore';
import screenStyles from '../screens.module.css';
import styles from './home.module.css';

const SEARCH_PARAM = 'search';

export function HomeScreen() {
    const navigate = useNavigate();
    const home = useHomeState();
    const [searchParams] = useSearchParams();
    const isSearchOpen = searchParams.has(SEARCH_PARAM);

    useEffect(() => {
        enterHome(() => navigate(routes.profileEdit));
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
        if (!canLoadMore || !trigger) return;
        const observer = new IntersectionObserver(([entry]) => {
            if (!entry.isIntersecting || requestedCount.current === itemCount) return;
            requestedCount.current = itemCount;
            loadMore();
        }, { rootMargin: `0px 0px ${LOAD_MORE_THRESHOLD * LISTING_ITEM_HEIGHT}px 0px` });
        observer.observe(trigger);
        return () => observer.disconnect();
    }, [canLoadMore, itemCount]);

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
                <DeliveryFeeFilter maxDeliveryFee={home.maxDeliveryFee} />
            </div>
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
        </div>
    );
}

function ListingItem({ furniture, index, count, onClick }: { furniture: FurnitureSummary; index: number; count: number; onClick: () => void }) {
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
                <>
                    <div className={`${screenStyles['price-primary']} type-body-large-em`}>{formatWon(furniture.totalPrice)}</div>
                    <div className="type-label-medium">배송비 {formatWon(furniture.deliveryFee)} 포함</div>
                    {furniture.hasPurchaseRequest && (
                        <div className={`${screenStyles['tertiary-text']} type-label-medium`}>{PAYMENT_IN_PROGRESS_LABEL}</div>
                    )}
                </>
            )}
            trailing={<span className="type-label-medium">{formatRegisteredDate(furniture.createdAt)}</span>}
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
    const results = searchFurnitures(home, query);
    return (
        <div className={styles['search-layer']}>
            <div className={styles['search-bar']}>
                <IconButton icon="arrowBack" label="검색 닫기" onClick={navigateBack} />
                <input
                    aria-label="중고 가구 검색"
                    autoFocus
                    className={styles['search-bar__input']}
                    enterKeyHint="search"
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="중고 가구 검색"
                    type="search"
                    value={query}
                />
            </div>
            {!query.trim() && (
                <SettyMessage description="판매 중인 가구의 제목으로 찾아드려요." style={{ marginTop: 48 }} title="어떤 가구를 찾으세요?" />
            )}
            {query.trim() && results.length === 0 && (
                <SettyMessage description="다른 단어로 다시 찾아보세요." style={{ marginTop: 48 }} title="검색 결과가 없어요" />
            )}
            {results.length > 0 && (
                <div className={styles['search-results']}>
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
                </div>
            )}
        </div>
    );
}
