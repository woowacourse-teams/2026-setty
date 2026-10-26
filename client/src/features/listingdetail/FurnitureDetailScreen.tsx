import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { trackEvent } from '../../analytics/posthog';
import { getPriceVariant } from '../../analytics/priceScenario';
import { isUnauthorized, toUserMessage } from '../../api/errors';
import { ApiError, NetworkError } from '../../api/http';
import { favoriteRepository, furnitureRepository } from '../../api/repositories';
import {
    categoryLabel,
    formatWon,
    isPaymentInProgress,
    isPurchasable,
    type FurnitureDetail
} from '../../model/furniture';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
    BackIconButton,
    BottomBar,
    Button,
    CenterFill,
    FlexibleTopAppBar,
    IconButton,
    LoadingIndicator,
    SaleStatusBadge,
    Scaffold,
    SectionTitle,
    SettyMessage
} from '../../ui/components';
import { shapeStyle } from '../../ui/shapes';
import { showSnackbar } from '../../ui/snackbar';
import screenStyles from '../screens.module.css';
import styles from './detail.module.css';

type DetailError = 'NOT_FOUND' | 'NETWORK_ERROR' | 'UNKNOWN_ERROR';

type DetailState =
    | { status: 'loading' }
    | { status: 'success'; listing: FurnitureDetail }
    | { status: 'error'; reason: DetailError };

type FavoriteState = {
    // 찜 여부를 아직 모르면 null
    isFavorited: boolean | null;
    isUpdating: boolean;
};

function toDetailError(error: unknown): DetailError {
    if (error instanceof ApiError) return error.code === 'LISTING_NOT_FOUND' ? 'NOT_FOUND' : 'UNKNOWN_ERROR';
    if (error instanceof NetworkError) return 'NETWORK_ERROR';
    return 'UNKNOWN_ERROR';
}

const detailErrorMessages: Record<DetailError, string> = {
    NOT_FOUND: '존재하지 않는 매물입니다',
    NETWORK_ERROR: '네트워크 에러가 발생했습니다',
    UNKNOWN_ERROR: '알 수 없는 에러가 발생했습니다'
};

export function FurnitureDetailScreen() {
    const { listingId } = useParams();
    const furnitureId = Number(listingId);
    const navigate = useNavigate();
    const location = useLocation();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const [state, setState] = useState<DetailState>({ status: 'loading' });
    const [favorite, setFavorite] = useState<FavoriteState>({ isFavorited: null, isUpdating: false });
    const favoriteRef = useRef(favorite);
    favoriteRef.current = favorite;

    const load = useCallback(() => {
        setState({ status: 'loading' });
        furnitureRepository.getFurnitureDetail(furnitureId)
            .then((listing) => setState({ status: 'success', listing }))
            .catch((error: unknown) => setState({ status: 'error', reason: toDetailError(error) }));
    }, [furnitureId]);

    useEffect(() => {
        load();
        // 찜 여부는 부가 정보라 실패해도 알리지 않고 토글만 숨긴다
        favoriteRepository.isFavorited(furnitureId)
            .then((isFavorited) => setFavorite((current) => ({ ...current, isFavorited })))
            .catch(() => undefined);
    }, [furnitureId, load]);

    useEffect(() => {
        if (state.status === 'success') trackEvent('product_viewed', { listing_id: state.listing.id });
    }, [state]);

    // 결제를 마치고 돌아오면 완료를 알리고 주문 내역으로 갈 수 있게 한다
    const paymentCompleted = (location.state as { paymentCompleted?: boolean } | null)?.paymentCompleted === true;
    useEffect(() => {
        if (!paymentCompleted) return;
        navigate(location.pathname, { replace: true, state: null });
        showSnackbar('결제가 완료되었습니다', { actionLabel: '주문 내역', onAction: () => navigate(routes.orders), long: true });
    }, [paymentCompleted, location.pathname, navigate]);

    const toggleFavorite = async () => {
        const { isFavorited, isUpdating } = favoriteRef.current;
        if (isFavorited === null || isUpdating) return;
        // 먼저 바꿔 보여주고 실패하면 되돌린다
        setFavorite({ isFavorited: !isFavorited, isUpdating: true });
        try {
            if (isFavorited) {
                await favoriteRepository.remove(furnitureId);
            } else {
                await favoriteRepository.add(furnitureId);
            }
            setFavorite((current) => ({ ...current, isUpdating: false }));
            showSnackbar(isFavorited ? '찜을 해제했습니다' : '찜했습니다');
        } catch (error) {
            setFavorite({ isFavorited, isUpdating: false });
            if (isUnauthorized(error)) {
                navigateToLogin();
            } else {
                showSnackbar(toUserMessage(error));
            }
        }
    };

    if (!Number.isSafeInteger(furnitureId) || furnitureId <= 0) {
        return <CenterFill><SettyMessage title={detailErrorMessages.NOT_FOUND} /></CenterFill>;
    }

    const listing = state.status === 'success' ? state.listing : null;
    const titleRow = (
        <span className={styles['title-row']}>
            <span className={styles['title-row__text']}>{listing?.title ?? ''}</span>
            {listing && <SaleStatusBadge isPaymentInProgress={isPaymentInProgress(listing)} saleStatus={listing.saleStatus} />}
        </span>
    );

    return (
        <Scaffold
            bottomBar={listing && (
                <BottomBar>
                    <Button
                        disabled={!isPurchasable(listing)}
                        fullWidth
                        onClick={() => navigate(routes.payment(listing.id), { state: { orderName: listing.title, amount: listing.totalPrice } })}
                        size="medium"
                    >
                        {isPurchasable(listing)
                            ? '결제하고 주문하기'
                            : isPaymentInProgress(listing) ? '다른 구매자가 결제 중이에요' : '구매할 수 없는 매물입니다'}
                    </Button>
                </BottomBar>
            )}
            topBar={(
                <FlexibleTopAppBar
                    actions={listing && favorite.isFavorited !== null && (
                        <IconButton
                            aria-pressed={favorite.isFavorited}
                            disabled={favorite.isUpdating}
                            icon={favorite.isFavorited ? 'favoriteFilled' : 'favorite'}
                            label={favorite.isFavorited ? '찜 해제' : '찜하기'}
                            onClick={() => void toggleFavorite()}
                            variant={favorite.isFavorited ? 'tonal' : 'tonal-off'}
                        />
                    )}
                    navigation={<BackIconButton onBack={navigateBack} />}
                    subtitle={listing && [categoryLabel(listing.category), listing.dongName].filter(Boolean).join(' · ')}
                    title={titleRow}
                />
            )}
        >
            {state.status === 'loading' && <CenterFill><LoadingIndicator /></CenterFill>}
            {state.status === 'error' && (
                <CenterFill>
                    <SettyMessage
                        action={state.reason === 'NOT_FOUND'
                            ? <Button onClick={navigateBack}>목록으로</Button>
                            : <Button onClick={load}>다시 시도</Button>}
                        shape="softBurst"
                        shapeColor="var(--error-container)"
                        title={detailErrorMessages[state.reason]}
                    />
                </CenterFill>
            )}
            {listing && <ListingDetailBody listing={listing} />}
        </Scaffold>
    );
}

function ListingDetailBody({ listing }: { listing: FurnitureDetail }) {
    const priceVariant = getPriceVariant();
    const showDeliveryCosts = priceVariant === 'B';
    return (
        <div className={styles.body} data-price-variant={priceVariant}>
            <ListingImageCarousel imageUrls={listing.imageUrls} title={listing.title} />
            <div className={styles['spec-row']}>
                <div className={styles['grade-card']}>
                    <div className={`${styles['grade-card__shape']} type-headline-large-em`} style={shapeStyle('cookie9')}>
                        {listing.conditionGrade}
                    </div>
                    <span className="type-body-medium-em">상태 등급</span>
                </div>
                <div className={styles['dimensions-card']}>
                    <span className={`${styles['dimensions-card__label']} type-body-medium-em`}>크기 (cm)</span>
                    <div className={styles['dimensions-card__values']}>
                        <DimensionValue label="W" value={listing.widthCm} />
                        <DimensionValue label="D" value={listing.depthCm} />
                        <DimensionValue label="H" value={listing.heightCm} />
                    </div>
                </div>
            </div>
            <section className={styles.section}>
                <SectionTitle>가격 정보</SectionTitle>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    <PriceRow amount={listing.price} isFirst label="매물 가격" />
                    {showDeliveryCosts && (
                        <>
                            <PriceRow amount={listing.deliveryFee} label="예상 배송비" />
                            <div className={screenStyles['total-row']}>
                                <span className="type-title-medium-em">총 결제 예상액</span>
                                <span className={`${screenStyles['total-row__amount']} type-headline-large-em`}>{formatWon(listing.totalPrice)}</span>
                            </div>
                        </>
                    )}
                </div>
            </section>
            <section className={styles.section}>
                <SectionTitle>상세 설명</SectionTitle>
                <p className={`${styles.description} type-body-large-em`}>{listing.description}</p>
            </section>
        </div>
    );
}

function PriceRow({ label, amount, isFirst = false }: { label: string; amount: number; isFirst?: boolean }) {
    return (
        <div className={`${styles['price-row']} ${isFirst ? styles['price-row--first'] : ''}`}>
            <span className={`${styles['price-row__label']} type-body-large-em`}>{label}</span>
            <span className="type-body-large-em">{formatWon(amount)}</span>
        </div>
    );
}

function DimensionValue({ label, value }: { label: string; value: number }) {
    return (
        <div className={styles.dimension}>
            <span className="type-headline-small-em">{value}</span>
            <span className={`${styles.dimension__label} type-body-medium-em`}>{label}</span>
        </div>
    );
}

/** 한 장씩 넘기는 사진 목록. 좌우 16px 여백 안에 사진 한 장이 꽉 차게 보인다. */
function ListingImageCarousel({ imageUrls, title }: { imageUrls: string[]; title: string }) {
    if (imageUrls.length === 0) {
        return <div className={`${styles['carousel-empty']} type-body-medium-em`}>등록된 사진이 없습니다</div>;
    }
    return (
        <div className={styles.carousel}>
            {imageUrls.map((url, index) => (
                <div className={styles.carousel__item} key={url}>
                    <img alt={`${title} 사진 ${index + 1}`} loading={index === 0 ? 'eager' : 'lazy'} src={url} />
                </div>
            ))}
        </div>
    );
}
