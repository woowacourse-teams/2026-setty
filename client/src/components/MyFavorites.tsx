import { useEffect, useState } from 'react';
import { fetchMyFavorites, removeFavorite } from '../api/favorites';
import type { ListingItem } from '../api/listings';

type MyFavoritesProps = {
    onSelect: (listingId: number) => void;
};

export function MyFavorites({ onSelect }: MyFavoritesProps) {
    const [items, setItems] = useState<ListingItem[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [removeMessage, setRemoveMessage] = useState<string | null>(null);
    const [removingId, setRemovingId] = useState<number | null>(null);

    const load = async () => {
        setIsLoading(true);
        setError(null);
        try {
            setItems(await fetchMyFavorites());
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '찜 목록을 불러오지 못했습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { void load(); }, []);

    const remove = async (listingId: number) => {
        setRemovingId(listingId);
        setRemoveMessage(null);
        try {
            await removeFavorite(listingId);
            setItems((current) => current.filter((item) => item.id !== listingId));
        } catch (reason) {
            setRemoveMessage(reason instanceof Error ? reason.message : '찜 해제를 완료하지 못했습니다.');
        } finally {
            setRemovingId(null);
        }
    };

    return (
        <section className="my-orders my-favorites" aria-labelledby="my-favorites-title">
            <div className="my-orders__heading">
                <h1 id="my-favorites-title">찜한 매물 <span>{items.length}</span></h1>
            </div>
            {isLoading && <p className="product-grid-message">찜 목록을 불러오는 중입니다.</p>}
            {error && <div className="product-grid-message"><p>{error}</p><button onClick={() => void load()} type="button">다시 시도</button></div>}
            {!isLoading && !error && items.length === 0 && <p className="product-grid-message">찜한 가구가 없습니다.</p>}
            {!isLoading && !error && items.length > 0 && (
                <div className="my-orders__list" aria-label="찜한 매물 목록">
                    {items.map((item) => (
                        <article className="my-orders__item my-favorites__item" key={item.id}>
                            <button className="my-favorites__select" onClick={() => onSelect(item.id)} type="button">
                                <span className="my-favorites__thumbnail">
                                    {item.thumbnailUrl && <img src={item.thumbnailUrl} alt="" />}
                                </span>
                                <span className="my-orders__details">
                                    <h2>{item.title}</h2>
                                    <p>매물가 {item.price.toLocaleString('ko-KR')}원 <i /> 배송비 {item.deliveryFee.toLocaleString('ko-KR')}원</p>
                                </span>
                            </button>
                            <strong className="my-orders__total">{item.totalPrice.toLocaleString('ko-KR')}원</strong>
                            <button
                                className="my-favorites__remove"
                                disabled={removingId === item.id}
                                onClick={() => void remove(item.id)}
                                type="button"
                            >
                                찜 해제
                            </button>
                        </article>
                    ))}
                </div>
            )}
            {removeMessage && <p className="product-grid-message" role="status">{removeMessage}</p>}
        </section>
    );
}
