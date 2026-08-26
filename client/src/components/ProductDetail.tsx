import { useState } from 'react';

const productImages = [
    { src: '/images/listings/rattan-chair.png', alt: '라탄 1인 라운지 체어 정면' },
    { src: '/images/listings/rattan-chair.png', alt: '라탄 1인 라운지 체어 측면' },
    { src: '/images/listings/rattan-chair.png', alt: '라탄 1인 라운지 체어 쿠션' },
];

function HeartIcon() {
    return (
        <svg aria-hidden="true" className="product-detail__heart-icon" viewBox="0 0 24 24">
            <path d="M20.8 8.7c0 5-8.8 10.2-8.8 10.2S3.2 13.7 3.2 8.7c0-2.4 1.8-4.2 4.2-4.2 1.9 0 3.4 1.1 4.1 2.6.7-1.5 2.2-2.6 4.1-2.6 2.4 0 4.2 1.8 4.2 4.2Z" />
        </svg>
    );
}

export function ProductDetail() {
    const [selectedImage, setSelectedImage] = useState(0);
    const productPrice = 88000;
    const deliveryFee = 27000;

    return (
        <section className="product-detail" aria-labelledby="product-title">
            <a className="product-detail__back-link" href="/">← 목록</a>
            <div className="product-detail__layout">
                <div className="product-detail__gallery">
                    <div className="product-detail__main-image">
                        <img src={productImages[selectedImage].src} alt={productImages[selectedImage].alt} />
                        <span className="product-detail__image-counter">{selectedImage + 1} / {productImages.length}</span>
                    </div>
                    <div className="product-detail__thumbnails" aria-label="상품 이미지 선택">
                        {productImages.map((image, index) => (
                            <button
                                className={`product-detail__thumbnail ${index === selectedImage ? 'product-detail__thumbnail--selected' : ''}`}
                                key={`${image.src}-${index}`}
                                type="button"
                                aria-label={`${index + 1}번째 이미지 보기`}
                                aria-pressed={index === selectedImage}
                                onClick={() => setSelectedImage(index)}
                            >
                                <img src={image.src} alt="" />
                                <span>{String(index + 1).padStart(2, '0')}</span>
                            </button>
                        ))}
                    </div>
                </div>

                <article className="product-detail__information">
                    <div className="product-detail__metadata">
                        <span className="product-detail__status">판매중</span>
                        <span>의자</span><i /> <span>A급</span><i /> <span>사용감 적음</span>
                    </div>
                    <h1 id="product-title">라탄 1인 라운지 체어</h1>
                    <span className="product-detail__dimensions">W700 × D780 × H900</span>

                    <dl className="product-detail__price-list">
                        <div><dt>매물 가격</dt><dd>{productPrice.toLocaleString('ko-KR')}원</dd></div>
                        <div><dt>예상 배송비</dt><dd>{deliveryFee.toLocaleString('ko-KR')}원</dd></div>
                    </dl>
                    <div className="product-detail__total">
                        <span>총 결제 예상액</span>
                        <strong>{(productPrice + deliveryFee).toLocaleString('ko-KR')}원</strong>
                    </div>
                    <div className="product-detail__actions">
                        <button className="product-detail__like-button" type="button" aria-label="찜하기"><HeartIcon /></button>
                        <button className="product-detail__purchase-button" type="button">구매 · 배송 요청하기</button>
                    </div>
                    <div className="product-detail__description">
                        <h2>상세 설명</h2>
                        <p>라탄 결 손상 없이 관리했고 쿠션은 세탁 완료했어요.</p>
                    </div>
                </article>
            </div>
        </section>
    );
}
