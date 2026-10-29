import { useNavigate } from 'react-router';
import { routes } from '../../navigation';
import { BottomBar } from '../../ui/components';
import { Icon, type IconName } from '../../ui/Icon';
import styles from './driver.module.css';

export type DriverTab = 'requests' | 'shipments' | 'settlement';

const tabs: Array<{ tab: DriverTab; label: string; icon: IconName; path: string }> = [
    { tab: 'requests', label: '배차 요청', icon: 'inbox', path: routes.driverRequests },
    { tab: 'shipments', label: '내 배차', icon: 'localShipping', path: routes.driverShipments },
    { tab: 'settlement', label: '정산', icon: 'payments', path: routes.driverSettlement }
];

/** 배송원 화면 아래의 탭. 탭끼리는 기록을 쌓지 않고 바꾼다. */
export function DriverTabBar({ current }: { current: DriverTab }) {
    const navigate = useNavigate();
    return (
        <BottomBar>
            <nav aria-label="배송원 메뉴" className={styles['tab-bar']}>
                {tabs.map(({ tab, label, icon, path }) => (
                    <button
                        aria-current={tab === current ? 'page' : undefined}
                        className={`${styles.tab} ${tab === current ? styles['tab--active'] : ''}`}
                        key={tab}
                        onClick={() => {
                            if (tab !== current) navigate(path, { replace: true });
                        }}
                        type="button"
                    >
                        <span className={styles.tab__indicator}><Icon name={icon} /></span>
                        <span className="type-label-medium">{label}</span>
                    </button>
                ))}
            </nav>
        </BottomBar>
    );
}
