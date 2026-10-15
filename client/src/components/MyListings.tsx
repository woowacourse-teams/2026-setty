import { useEffect, useRef, useState } from 'react';
import { trackEvent } from '../analytics/posthog';
import { deleteListing, fetchMyListings, type MyListing } from '../api/listings';
import accountPagesStyles from '../styles/modules/AccountPages.module.css';
import productGridStyles from '../styles/modules/ProductGrid.module.css';

const categoryLabels: Record<string, string> = {
    SOFA: '소파', TABLE: '테이블', DESK: '책상', CHAIR: '의자', STORAGE: '수납장', BED: '침대'
};

type MyListingsProps = {
    onRegister: () => void;
    onEdit: (listingId: number) => void;
    onSelect: (listingId: number) => void;
};

function formatRegisteredAt(createdAt: string) {
    const date = new Date(createdAt);
    return `${date.getUTCMonth() + 1}.${date.getUTCDate()}`;
}

function statusLabel(status: MyListing['saleStatus']) {
    return status === 'AVAILABLE' ? '판매중' : status === 'RESERVED' ? '예약중' : '판매완료';
}

export function MyListings({ onRegister, onEdit, onSelect }: MyListingsProps) {
    const [items, setItems] = useState<MyListing[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const listRef = useRef<HTMLDivElement>(null);
    const observedSoldListingIds = useRef(new Set<number>());

    const load = async () => {
        setIsLoading(true);
        setError(null);
        try {
            setItems(await fetchMyListings());
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '내 가구 목록을 불러오지 못했습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        trackEvent('my_listings_viewed');
        void load();
    }, []);

    useEffect(() => {
        const list = listRef.current;
        if (!list) return;

        const soldListings = list.querySelectorAll<HTMLElement>('[data-sold-listing-id]');
        if (soldListings.length === 0) return;

        const observeSoldListing = (element: Element) => {
            const listingId = Number((element as HTMLElement).dataset.soldListingId);
            if (!Number.isSafeInteger(listingId) || observedSoldListingIds.current.has(listingId)) return;

            observedSoldListingIds.current.add(listingId);
            trackEvent('listing_status_observed', { listing_id: listingId, status: 'SOLD' });
        };

        if (typeof IntersectionObserver === 'undefined') {
            soldListings.forEach(observeSoldListing);
            return;
        }

        const observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (entry.isIntersecting) observeSoldListing(entry.target);
            });
        }, { threshold: 0.5 });

        soldListings.forEach((element) => observer.observe(element));
        return () => observer.disconnect();
    }, [items, isLoading]);

    const remove = async (listing: MyListing) => {
        if (!listing.canDelete || !window.confirm(`“${listing.title}” 매물을 내릴까요?`)) return;
        try {
            await deleteListing(listing.id);
            setItems((current) => current.filter((item) => item.id !== listing.id));
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '가구를 내리지 못했습니다.');
        }
    };

    return (
        <section className={accountPagesStyles['my-furniture']} aria-labelledby="my-furniture-title">
            <div className={accountPagesStyles['my-furniture__heading']}>
                <h1 id="my-furniture-title">내 가구 <span>{items.length}</span></h1>
                <button className={accountPagesStyles['my-furniture__register-button']} onClick={onRegister} type="button">새 가구 등록</button>
            </div>
            {isLoading && <p className={productGridStyles['product-grid-message']}>내 가구 목록을 불러오는 중입니다.</p>}
            {error && <div className={productGridStyles['product-grid-message']}><p>{error}</p><button onClick={() => void load()} type="button">다시 시도</button></div>}
            {!isLoading && !error && items.length === 0 && <p className={productGridStyles['product-grid-message']}>등록한 가구가 없습니다.</p>}
            {!isLoading && !error && items.length > 0 && (
                <div ref={listRef} className={accountPagesStyles['my-furniture__list']} aria-label="등록한 가구 목록">
                    {items.map((item) => (
                        <article
                            className={[accountPagesStyles['my-furniture__item'], item.saleStatus === 'SOLD' && accountPagesStyles['my-furniture__item--sold']].filter(Boolean).join(' ')}
                            data-sold-listing-id={item.saleStatus === 'SOLD' ? item.id : undefined}
                            key={item.id}
                        >
                            <button className={accountPagesStyles['my-furniture__select']} onClick={() => onSelect(item.id)} type="button">
                                <span className={accountPagesStyles['my-furniture__thumbnail']}>
                                    {item.thumbnailUrl && <img alt="" src={item.thumbnailUrl} />}
                                </span>
                                <span className={accountPagesStyles['my-furniture__details']}>
                                    <strong>{item.title}</strong>
                                    <span>{categoryLabels[item.category]} <i /> {item.conditionGrade}급 <i /> {formatRegisteredAt(item.createdAt)} 등록</span>
                                </span>
                            </button>
                            <strong className={accountPagesStyles['my-furniture__price']}>{item.price.toLocaleString('ko-KR')}원</strong>
                            <span className={[accountPagesStyles['my-furniture__status'], item.saleStatus === 'RESERVED' && accountPagesStyles['my-furniture__status--reserved']].filter(Boolean).join(' ')}>{statusLabel(item.saleStatus)}</span>
                            <div className={accountPagesStyles['my-furniture__controls']}>
                                {item.canUpdate && <button onClick={() => onEdit(item.id)} type="button">수정</button>}
                                {item.canDelete && <button onClick={() => void remove(item)} type="button">내리기</button>}
                            </div>
                        </article>
                    ))}
                </div>
            )}
        </section>
    );
}
