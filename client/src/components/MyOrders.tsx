import { useEffect, useRef, useState } from 'react';
import { fetchMyOrders, requestOrderCancellation, type DeliveryStatus, type MyOrder, type OrderStatus } from '../api/orders';
import accountPagesStyles from '../styles/modules/AccountPages.module.css';
import productGridStyles from '../styles/modules/ProductGrid.module.css';

const deliveryStatusLabels: Record<DeliveryStatus, string> = {
    PENDING: '결제 대기',
    REQUESTED: '배송 요청',
    ACCEPTED: '배차 완료',
    PICKED_UP: '수거 완료',
    DELIVERED: '배송 완료'
};

function statusLabel(order: MyOrder) {
    switch (order.orderStatus) {
        case 'PENDING': return '결제 대기';
        case 'CANCEL_PENDING': return '취소 대기 중';
        case 'CANCELLED': return '취소 확정';
        case 'EXPIRED': return '주문 만료';
        case 'CONFIRMED': return deliveryStatusLabels[order.deliveryStatus];
    }
}

function statusClassName(order: MyOrder) {
    const status = order.orderStatus === 'CONFIRMED' ? order.deliveryStatus : order.orderStatus;
    return accountPagesStyles[`my-orders__status--${status.toLowerCase().replace('_', '-')}`];
}

type MyOrdersProps = {
    onSelect: (listingId: number) => void;
};

export function MyOrders({ onSelect }: MyOrdersProps) {
    const [items, setItems] = useState<MyOrder[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [pollError, setPollError] = useState<string | null>(null);
    const [cancellingId, setCancellingId] = useState<number | null>(null);
    const previousStatuses = useRef(new Map<number, OrderStatus>());
    const hasPendingCancellation = items.some((item) => item.orderStatus === 'CANCEL_PENDING');

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

    useEffect(() => {
        for (const item of items) {
            if (previousStatuses.current.get(item.id) !== 'CANCEL_PENDING') continue;
            if (item.orderStatus === 'CANCELLED') {
                setNotice('주문 취소가 확정되었습니다. 환불은 별도로 처리됩니다.');
            } else if (item.orderStatus === 'CONFIRMED') {
                setNotice('주문 취소에 실패했습니다. 배송 상태를 확인해 주세요.');
            }
        }
        previousStatuses.current = new Map(items.map((item) => [item.id, item.orderStatus]));
    }, [items]);

    useEffect(() => {
        if (!hasPendingCancellation) return;

        let active = true;
        let timer: number;
        const refresh = async () => {
            try {
                const orders = await fetchMyOrders();
                if (!active) return;
                setItems(orders);
                setPollError(null);
            } catch {
                if (active) setPollError('취소 결과를 확인하지 못했습니다. 자동으로 다시 확인합니다.');
            }
            if (active) timer = window.setTimeout(() => { void refresh(); }, 3000);
        };
        timer = window.setTimeout(() => { void refresh(); }, 3000);
        return () => {
            active = false;
            window.clearTimeout(timer);
        };
    }, [hasPendingCancellation]);

    const cancel = async (order: MyOrder) => {
        const name = order.listing?.name ?? '이 주문';
        if (!window.confirm(`“${name}”의 취소를 요청할까요?\n배송 진행 상황에 따라 취소가 거절될 수 있습니다.`)) return;

        setCancellingId(order.id);
        setNotice(null);
        try {
            const result = await requestOrderCancellation(order.id);
            setItems((current) => current.map((item) => item.id === order.id ? { ...item, orderStatus: result.orderStatus } : item));
            setNotice(result.message);
        } catch (reason) {
            setNotice(reason instanceof Error ? reason.message : '주문 취소 요청을 접수하지 못했습니다.');
        } finally {
            setCancellingId(null);
        }
    };

    return (
        <section className={accountPagesStyles['my-orders']} aria-labelledby="my-orders-title">
            <div className={accountPagesStyles['my-orders__heading']}>
                <h1 id="my-orders-title">내 주문 <span>{items.length}</span></h1>
            </div>
            {isLoading && <p className={productGridStyles['product-grid-message']}>내 주문 목록을 불러오는 중입니다.</p>}
            {error && <div className={productGridStyles['product-grid-message']}><p>{error}</p><button onClick={() => void load()} type="button">다시 시도</button></div>}
            {!isLoading && !error && items.length === 0 && <p className={productGridStyles['product-grid-message']}>주문한 가구가 없습니다.</p>}
            {!isLoading && !error && items.length > 0 && (
                <div className={accountPagesStyles['my-orders__list']} aria-label="내 주문 목록">
                    {items.map((item) => (
                        <article className={accountPagesStyles['my-orders__item']} key={item.id}>
                            {item.listing ? (
                                <button className={accountPagesStyles['my-orders__select']} onClick={() => onSelect(item.listing!.id)} type="button">
                                    <span className={accountPagesStyles['my-orders__thumbnail']}>
                                        {item.listing.thumbnailUrl && <img alt="" src={item.listing.thumbnailUrl} />}
                                    </span>
                                    <span className={accountPagesStyles['my-orders__details']}>
                                        <strong>{item.listing.name}</strong>
                                        <span>매물가 {item.listing.price.toLocaleString('ko-KR')}원 <i /> 배송비 {item.listing.deliveryFee.toLocaleString('ko-KR')}원</span>
                                    </span>
                                </button>
                            ) : <span className={accountPagesStyles['my-orders__unavailable']}>판매가 종료된 매물</span>}
                            <strong className={accountPagesStyles['my-orders__total']}>{item.listing ? `${(item.listing.price + item.listing.deliveryFee).toLocaleString('ko-KR')}원` : '-'}</strong>
                            <div className={accountPagesStyles['my-orders__actions']}>
                                <span className={[accountPagesStyles['my-orders__status'], statusClassName(item)].filter(Boolean).join(' ')}>{statusLabel(item)}</span>
                                {item.orderStatus === 'CONFIRMED' && (
                                    <button className={accountPagesStyles['my-orders__cancel']} disabled={cancellingId !== null} onClick={() => void cancel(item)} type="button">
                                        {cancellingId === item.id ? '요청 중...' : '취소 요청'}
                                    </button>
                                )}
                            </div>
                        </article>
                    ))}
                </div>
            )}
            {notice && <p className={productGridStyles['product-grid-message']} role="status">{notice}</p>}
            {pollError && <p className={productGridStyles['product-grid-message']} role="status">{pollError}</p>}
        </section>
    );
}
