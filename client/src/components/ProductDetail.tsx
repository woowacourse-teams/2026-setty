import { useEffect, useState } from 'react';
import { fetchListing, type ListingDetail } from '../api/listings';
import { createOrder } from '../api/orders';

const categoryLabels: Record<string, string> = {
    SOFA: '소파', TABLE: '테이블', DESK: '책상', CHAIR: '의자', STORAGE: '수납장', BED: '침대'
};

function HeartIcon() {
    return (
        <svg aria-hidden="true" className="product-detail__heart-icon" viewBox="0 0 24 24">
            <path d="M20.8 8.7c0 5-8.8 10.2-8.8 10.2S3.2 13.7 3.2 8.7c0-2.4 1.8-4.2 4.2-4.2 1.9 0 3.4 1.1 4.1 2.6.7-1.5 2.2-2.6 4.1-2.6 2.4 0 4.2 1.8 4.2 4.2Z" />
        </svg>
    );
}

type ProductDetailProps = {
    listingId: number;
    onBack: () => void;
    onLoginRequired: () => void;
};

export function ProductDetail({ listingId, onBack, onLoginRequired }: ProductDetailProps) {
    const [selectedImage, setSelectedImage] = useState(0);
    const [listing, setListing] = useState<ListingDetail | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [purchaseMessage, setPurchaseMessage] = useState<string | null>(null);
    const [isPurchasing, setIsPurchasing] = useState(false);

    useEffect(() => {
        let isCurrent = true;
        setListing(null);
        setError(null);
        setSelectedImage(0);
        setPurchaseMessage(null);

        void fetchListing(listingId)
            .then((result) => isCurrent && setListing(result))
            .catch((reason: unknown) => isCurrent && setError(reason instanceof Error ? reason.message : '매물 상세를 불러오지 못했습니다.'));

        return () => { isCurrent = false; };
    }, [listingId]);

    if (error) {
        return <section className="product-grid-message"><p>{error}</p><button onClick={onBack} type="button">목록으로</button></section>;
    }

    if (!listing) {
        return <section className="product-grid-message" aria-live="polite">매물 상세를 불러오는 중입니다.</section>;
    }

    const images = listing.images;
    const selected = images[selectedImage] ?? images[0];

    const requestPurchase = async () => {
        if (!window.sessionStorage.getItem('setty:auth-token')) {
            onLoginRequired();
            return;
        }

        setIsPurchasing(true);
        setPurchaseMessage(null);
        try {
            await createOrder(listing.id);
            setPurchaseMessage('구매 및 배송 요청이 완료되었습니다. 내 주문에서 배송 상태를 확인할 수 있습니다.');
        } catch (reason) {
            const message = reason instanceof Error ? reason.message : '주문 요청을 완료하지 못했습니다.';
            setPurchaseMessage(message);
        } finally {
            setIsPurchasing(false);
        }
    };

    return (
        <section className="product-detail" aria-labelledby="product-title">
            <div className="product-detail__layout">
                <div className="product-detail__gallery">
                    <div className="product-detail__main-image">
                        {selected && <img src={selected.url} alt={`${listing.title} 대표 이미지`} />}
                        <span className="product-detail__image-counter">{images.length ? selectedImage + 1 : 0} / {images.length}</span>
                    </div>
                    <div className="product-detail__thumbnails" aria-label="상품 이미지 선택">
                        {images.map((image, index) => (
                            <button
                                className={`product-detail__thumbnail ${index === selectedImage ? 'product-detail__thumbnail--selected' : ''}`}
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

                <article className="product-detail__information">
                    <div className="product-detail__metadata">
                        <span className="product-detail__status">{listing.saleStatus === 'AVAILABLE' ? '판매중' : listing.saleStatus === 'RESERVED' ? '예약중' : '판매완료'}</span>
                        <span>{categoryLabels[listing.category]}</span><i /> <span>{listing.conditionGrade}급</span>
                    </div>
                    <h1 id="product-title">{listing.title}</h1>
                    <span className="product-detail__dimensions">W{listing.dimensions.widthCm} × D{listing.dimensions.depthCm} × H{listing.dimensions.heightCm} cm</span>

                    <dl className="product-detail__price-list">
                        <div><dt>매물 가격</dt><dd>{listing.price.toLocaleString('ko-KR')}원</dd></div>
                        <div><dt>예상 배송비</dt><dd>{listing.deliveryFee.toLocaleString('ko-KR')}원</dd></div>
                    </dl>
                    <div className="product-detail__total">
                        <span>총 결제 예상액</span>
                        <strong>{listing.totalPrice.toLocaleString('ko-KR')}원</strong>
                    </div>
                    <div className="product-detail__actions">
                        <button className="product-detail__like-button" type="button" aria-label="찜하기"><HeartIcon /></button>
                        <button className="product-detail__purchase-button" disabled={isPurchasing || listing.saleStatus !== 'AVAILABLE'} onClick={() => void requestPurchase()} type="button">
                            {isPurchasing ? '요청 중...' : listing.saleStatus === 'AVAILABLE' ? '구매 · 배송 요청하기' : '구매할 수 없는 매물입니다'}
                        </button>
                    </div>
                    {purchaseMessage && <p className="product-detail__purchase-message" role="status">{purchaseMessage}</p>}
                    <div className="product-detail__description">
                        <h2>상세 설명</h2>
                        <p>{listing.description}</p>
                    </div>
                </article>
            </div>
        </section>
    );
}
