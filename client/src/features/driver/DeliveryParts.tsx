import { useCallback } from 'react';
import { isUnauthorized, toUserMessage } from '../../api/errors';
import { formatWon } from '../../model/furniture';
import { categoryText, shipmentStatusLabel, shortAddress, type DeliveryStatus, type DeliverySummary } from '../../model/delivery';
import { useNavigateToDriverLogin } from '../../navigation';
import { SegmentedListItem, StatusBadge } from '../../ui/components';
import { Icon } from '../../ui/Icon';
import { shapeStyle } from '../../ui/shapes';
import { showSnackbar } from '../../ui/snackbar';
import styles from './driver.module.css';

/** 배송원 동작이 실패했을 때 로그인이 풀렸으면 배송원 로그인으로, 아니면 서버 문구를 알린다. */
export function useDriverErrorHandler() {
    const navigateToDriverLogin = useNavigateToDriverLogin();
    return useCallback((error: unknown) => {
        if (isUnauthorized(error)) {
            navigateToDriverLogin();
        } else {
            showSnackbar(toUserMessage(error));
        }
    }, [navigateToDriverLogin]);
}

export function ShipmentStatusBadge({ status }: { status: DeliveryStatus }) {
    const colors: Record<DeliveryStatus, [string, string]> = {
        REQUESTED: ['var(--primary-container)', 'var(--on-primary-container)'],
        ACCEPTED: ['var(--tertiary-container)', 'var(--on-tertiary-container)'],
        PICKED_UP: ['var(--primary-container)', 'var(--on-primary-container)'],
        DELIVERED: ['var(--surface-container-highest)', 'var(--on-surface-variant)']
    };
    const [containerColor, contentColor] = colors[status];
    return <StatusBadge containerColor={containerColor} contentColor={contentColor} text={shipmentStatusLabel(status)} />;
}

type DeliveryListItemProps = {
    delivery: DeliverySummary;
    index: number;
    count: number;
    showStatus?: boolean;
    onClick: () => void;
};

/** 배차 요청·내 배차 목록의 항목. 출발 동네 → 도착 동네와 배송비를 보여준다. */
export function DeliveryListItem({ delivery, index, count, showStatus = false, onClick }: DeliveryListItemProps) {
    const isDone = delivery.status === 'DELIVERED';
    return (
        <SegmentedListItem
            count={count}
            headline={<span className="type-title-medium">{delivery.itemName}</span>}
            index={index}
            leading={(
                <div className={`${styles['leading-shape']} ${isDone ? styles['leading-shape--done'] : ''}`} style={shapeStyle('cookie9')}>
                    <Icon name={isDone ? 'check' : 'localShipping'} />
                </div>
            )}
            onClick={onClick}
            overline={categoryText(delivery.category)}
            supporting={(
                <span style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    <span className={`${styles.route} type-body-medium`}>
                        {shortAddress(delivery.pickupAddress)} → {shortAddress(delivery.deliveryAddress)}
                    </span>
                    <span className={`${styles.fee} type-body-large-em`}>{formatWon(delivery.deliveryFee)}</span>
                </span>
            )}
            trailing={showStatus ? <ShipmentStatusBadge status={delivery.status} /> : <Icon name="chevronRight" />}
        />
    );
}

type RoutePointProps = {
    label: string;
    address: string;
    phoneNumber?: string;
};

export function RoutePoint({ label, address, phoneNumber }: RoutePointProps) {
    return (
        <div className={styles.point}>
            <span className={`${styles.point__label} type-label-large`}>{label}</span>
            <span className={`${styles.point__address} type-body-large-em`}>{address}</span>
            {phoneNumber && (
                <a className={`${styles['phone-link']} type-label-large`} href={`tel:${phoneNumber}`}>
                    <Icon name="call" size={18} />
                    {phoneNumber}
                </a>
            )}
        </div>
    );
}
