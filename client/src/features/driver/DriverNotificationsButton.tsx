import { useEffect, useState } from 'react';
import { deliveryRepository } from '../../api/deliveryRepository';
import { IconButton } from '../../ui/components';
import styles from './driverNotifications.module.css';

/** 배송원 화면 상단의 알림 종. 받은 알림 중 안 읽은 게 있으면 빨간 점을 띄운다. 회원 홈의 알림 버튼과 같은 모양이다. */
export function DriverNotificationsButton({ onClick }: { onClick: () => void }) {
    const [hasUnread, setHasUnread] = useState(false);

    useEffect(() => {
        const refresh = () => {
            deliveryRepository.getNotifications()
                .then((notifications) => setHasUnread(notifications.some((notification) => !notification.read)))
                // 점은 부가 표시라 실패해도 화면에는 알리지 않는다
                .catch(() => undefined);
        };
        refresh();
        const refreshWhenVisible = () => {
            if (document.visibilityState === 'visible') refresh();
        };
        document.addEventListener('visibilitychange', refreshWhenVisible);
        return () => document.removeEventListener('visibilitychange', refreshWhenVisible);
    }, []);

    return (
        <span className={styles.button}>
            <IconButton icon="notifications" label={hasUnread ? '받은 알림, 새 알림 있음' : '받은 알림'} onClick={onClick} />
            {hasUnread && <span aria-hidden className={styles['unread-dot']} />}
        </span>
    );
}
