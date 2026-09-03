import { useEffect, useState } from 'react';
import { fetchMyOrders, type DeliveryStatus, type MyOrder } from '../api/orders';
import accountPagesStyles from '../styles/modules/AccountPages.module.css';
import productGridStyles from '../styles/modules/ProductGrid.module.css';

const deliveryStatusLabels: Record<DeliveryStatus, string> = {
    REQUESTED: '배송 요청',
    ACCEPTED: '배차 완료',
    PICKED_UP: '수거 완료',
    DELIVERED: '배송 완료'
};

export function MyOrders() {
    const [items, setItems] = useState<MyOrder[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = async () => {
        setIsLoading(true);
        setError(null);
        try {
            setItems(await fetchMyOrders());
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '내 주문 목록을 불러오지 못했습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { void load(); }, []);

    return (
        <section className={accountPagesStyles['my-orders']} aria-labelledby="my-orders-title">
            <div className={accountPagesStyles['my-orders__heading']}>
                <h1 id="my-orders-title">내 주문 <span>{items.length}</span></h1>
            </div>
            {isLoading && <p className={productGridStyles['product-grid-message']} role="status">내 주문 목록을 불러오는 중입니다.</p>}
            {error && <div className={productGridStyles['product-grid-message']} role="alert"><p>{error}</p><button onClick={() => void load()} type="button">다시 시도</button></div>}
            {!isLoading && !error && items.length === 0 && <p className={productGridStyles['product-grid-message']} role="status">주문한 가구가 없습니다.</p>}
            {!isLoading && !error && items.length > 0 && (
                <ul className={accountPagesStyles['my-orders__list']} aria-label="내 주문 목록">
                    {items.map((item) => (
                        <li className={accountPagesStyles['my-orders__item']} key={item.id}>
                            <div className={accountPagesStyles['my-orders__details']}>
                                <h2>{item.listing.name}</h2>
                                <p>매물가 {item.listing.price.toLocaleString('ko-KR')}원 <i aria-hidden="true" /> 배송비 {item.listing.deliveryFee.toLocaleString('ko-KR')}원</p>
                            </div>
                            <strong className={accountPagesStyles['my-orders__total']}>{(item.listing.price + item.listing.deliveryFee).toLocaleString('ko-KR')}원</strong>
                            <span className={[accountPagesStyles['my-orders__status'], accountPagesStyles[`my-orders__status--${item.deliveryStatus.toLowerCase()}`]].filter(Boolean).join(' ')}>{deliveryStatusLabels[item.deliveryStatus]}</span>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
