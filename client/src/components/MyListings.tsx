import { useEffect, useState } from 'react';
import { deleteListing, fetchMyListings, type MyListing } from '../api/listings';
import { cx } from '../styles/styles';

const categoryLabels: Record<string, string> = {
    SOFA: '소파', TABLE: '테이블', DESK: '책상', CHAIR: '의자', STORAGE: '수납장', BED: '침대'
};

type MyListingsProps = {
    onRegister: () => void;
    onEdit: (listingId: number) => void;
};

function formatRegisteredAt(createdAt: string) {
    const date = new Date(createdAt);
    return `${date.getUTCMonth() + 1}.${date.getUTCDate()}`;
}

function statusLabel(status: MyListing['saleStatus']) {
    return status === 'AVAILABLE' ? '판매중' : status === 'RESERVED' ? '예약중' : '판매완료';
}

export function MyListings({ onRegister, onEdit }: MyListingsProps) {
    const [items, setItems] = useState<MyListing[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

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

    useEffect(() => { void load(); }, []);

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
        <section className={cx('my-furniture')} aria-labelledby="my-furniture-title">
            <div className={cx('my-furniture__heading')}>
                <h1 id="my-furniture-title">내 가구 <span>{items.length}</span></h1>
                <button className={cx('my-furniture__register-button')} onClick={onRegister} type="button">새 가구 등록</button>
            </div>
            {isLoading && <p className={cx('product-grid-message')}>내 가구 목록을 불러오는 중입니다.</p>}
            {error && <div className={cx('product-grid-message')}><p>{error}</p><button onClick={() => void load()} type="button">다시 시도</button></div>}
            {!isLoading && !error && items.length === 0 && <p className={cx('product-grid-message')}>등록한 가구가 없습니다.</p>}
            {!isLoading && !error && items.length > 0 && (
                <div className={cx('my-furniture__list')} aria-label="등록한 가구 목록">
                    {items.map((item) => (
                        <article className={cx('my-furniture__item', item.saleStatus === 'SOLD' && 'my-furniture__item--sold')} key={item.id}>
                            <div className={cx('my-furniture__thumbnail')}>
                                {item.thumbnailUrl && <img alt="" src={item.thumbnailUrl} />}
                            </div>
                            <div className={cx('my-furniture__details')}>
                                <h2>{item.title}</h2>
                                <p>{categoryLabels[item.category]} <i /> {item.conditionGrade}급 <i /> {formatRegisteredAt(item.createdAt)} 등록</p>
                            </div>
                            <strong className={cx('my-furniture__price')}>{item.price.toLocaleString('ko-KR')}원</strong>
                            <span className={cx('my-furniture__status', item.saleStatus === 'RESERVED' && 'my-furniture__status--reserved')}>{statusLabel(item.saleStatus)}</span>
                            <div className={cx('my-furniture__controls')}>
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
