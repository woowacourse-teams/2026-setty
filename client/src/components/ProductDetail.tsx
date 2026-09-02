import { ArrowLeft } from '@phosphor-icons/react/dist/icons/ArrowLeft';
import { Heart } from '@phosphor-icons/react/dist/icons/Heart';
import { useEffect, useState } from 'react';
import { addFavorite, fetchFavoriteStatus, removeFavorite } from '../api/favorites';
import { fetchListing, type ListingDetail } from '../api/listings';
import { PaymentCheckout } from './PaymentCheckout';
import { cx } from '../styles/styles';

const categoryLabels: Record<string, string> = {
    SOFA: '소파', TABLE: '테이블', DESK: '책상', CHAIR: '의자', STORAGE: '수납장', BED: '침대'
};

type ProductDetailProps = {
    listingId: number;
    isLoggedIn: boolean;
    onBack: () => void;
    onLoginRequired: () => void;
};

export function ProductDetail({ listingId, isLoggedIn, onBack, onLoginRequired }: ProductDetailProps) {
    const [selectedImage, setSelectedImage] = useState(0);
    const [listing, setListing] = useState<ListingDetail | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [purchaseMessage, setPurchaseMessage] = useState<string | null>(null);
    const [isCheckoutOpen, setIsCheckoutOpen] = useState(false);
    const [isFavorited, setIsFavorited] = useState(false);
    const [isFavoriteBusy, setIsFavoriteBusy] = useState(false);

    useEffect(() => {
        if (!isLoggedIn) {
            setIsFavorited(false);
            return;
        }

        let isCurrent = true;
        void fetchFavoriteStatus(listingId)
            .then((favorited) => isCurrent && setIsFavorited(favorited))
            .catch(() => isCurrent && setIsFavorited(false));

        return () => { isCurrent = false; };
    }, [listingId, isLoggedIn]);

    useEffect(() => {
        let isCurrent = true;
        setListing(null);
        setError(null);
        setSelectedImage(0);
        setPurchaseMessage(null);
        setIsCheckoutOpen(false);

        void fetchListing(listingId)
            .then((result) => isCurrent && setListing(result))
            .catch((reason: unknown) => isCurrent && setError(reason instanceof Error ? reason.message : '매물 상세를 불러오지 못했습니다.'));

        return () => { isCurrent = false; };
    }, [listingId]);

    if (error) {
        return <section className={cx('product-grid-message')}><p>{error}</p><button onClick={onBack} type="button">목록으로</button></section>;
    }

    if (!listing) {
        return <section className={cx('product-grid-message')} aria-live="polite">매물 상세를 불러오는 중입니다.</section>;
    }

    const images = listing.images;
    const selected = images[selectedImage] ?? images[0];

    const toggleFavorite = async () => {
        if (!isLoggedIn) {
            onLoginRequired();
            return;
        }

        const nextFavorited = !isFavorited;
        setIsFavorited(nextFavorited);
        setIsFavoriteBusy(true);
        try {
            await (nextFavorited ? addFavorite(listing.id) : removeFavorite(listing.id));
        } catch (reason) {
            setIsFavorited(!nextFavorited);
            setPurchaseMessage(reason instanceof Error ? reason.message : '찜 처리를 완료하지 못했습니다.');
        } finally {
            setIsFavoriteBusy(false);
        }
    };

    const openCheckout = () => {
        if (!window.sessionStorage.getItem('setty:auth-token')) {
            onLoginRequired();
            return;
        }
        setPurchaseMessage(null);
        setIsCheckoutOpen(true);
    };

    return (
        <section className={cx('product-detail')} aria-labelledby="product-title">
            <button className={cx('product-detail__back-button')} onClick={onBack} type="button">
                <ArrowLeft aria-hidden="true" weight="bold" />
                <span>목록으로</span>
            </button>
            <div className={cx('product-detail__layout')}>
                <div>
                    <div className={cx('product-detail__main-image')}>
                        {selected && <img src={selected.url} alt={`${listing.title} 대표 이미지`} />}
                        <span className={cx('product-detail__image-counter')}>{images.length ? selectedImage + 1 : 0} / {images.length}</span>
                    </div>
                    <div className={cx('product-detail__thumbnails')} aria-label="상품 이미지 선택">
                        {images.map((image, index) => (
                            <button
                                className={cx('product-detail__thumbnail', index === selectedImage && 'product-detail__thumbnail--selected')}
                                key={image.id}
                                type="button"
                                aria-label={`${index + 1}번째 이미지 보기`}
                                aria-pressed={index === selectedImage}
                                onClick={() => setSelectedImage(index)}
                            >
                                <img src={image.url} alt="" />
                                <span>{String(index + 1).padStart(2, '0')}</span>
                            </button>
                        ))}
                    </div>
                </div>

                <article className={cx('product-detail__information')}>
                    <div className={cx('product-detail__metadata')}>
                        <span className={cx('product-detail__status')}>{listing.saleStatus === 'AVAILABLE' ? '판매중' : listing.saleStatus === 'RESERVED' ? '예약중' : '판매완료'}</span>
                        <span>{categoryLabels[listing.category]}</span><i /> <span>{listing.conditionGrade}급</span>
                    </div>
                    <h1 id="product-title">{listing.title}</h1>
                    <span className={cx('product-detail__dimensions')}>W{listing.dimensions.widthCm} × D{listing.dimensions.depthCm} × H{listing.dimensions.heightCm} cm</span>

                    <dl className={cx('product-detail__price-list')}>
                        <div><dt>매물 가격</dt><dd>{listing.price.toLocaleString('ko-KR')}원</dd></div>
                        <div><dt>예상 배송비</dt><dd>{listing.deliveryFee.toLocaleString('ko-KR')}원</dd></div>
                    </dl>
                    <div className={cx('product-detail__total')}>
                        <span>총 결제 예상액</span>
                        <strong>{listing.totalPrice.toLocaleString('ko-KR')}원</strong>
                    </div>
                    <div className={cx('product-detail__actions')}>
                        <button
                            className={cx('product-detail__like-button')}
                            type="button"
                            aria-label={isFavorited ? '찜 해제' : '찜하기'}
                            aria-pressed={isFavorited}
                            disabled={isFavoriteBusy}
                            onClick={() => void toggleFavorite()}
                        >
                            <Heart
                                aria-hidden="true"
                                className={cx('product-detail__heart-icon', isFavorited && 'product-detail__heart-icon--favorited')}
                                weight={isFavorited ? 'fill' : 'regular'}
                            />
                        </button>
                        <button className={cx('product-detail__purchase-button')} disabled={listing.saleStatus !== 'AVAILABLE'} onClick={openCheckout} type="button">
                            {listing.saleStatus === 'AVAILABLE' ? '결제하고 주문하기' : '구매할 수 없는 매물입니다'}
                        </button>
                    </div>
                    {purchaseMessage && <p className={cx('product-detail__purchase-message')} role="status">{purchaseMessage}</p>}
                    <div className={cx('product-detail__description')}>
                        <h2>상세 설명</h2>
                        <p>{listing.description}</p>
                    </div>
                </article>
            </div>

            {isCheckoutOpen && (
                <PaymentCheckout
                    listingId={listing.id}
                    amount={listing.totalPrice}
                    orderName={listing.title}
                    onClose={() => setIsCheckoutOpen(false)}
                />
            )}
        </section>
    );
}
