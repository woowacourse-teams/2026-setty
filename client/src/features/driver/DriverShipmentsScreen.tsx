import { useNavigate } from 'react-router';
import { deliveryRepository } from '../../api/deliveryRepository';
import { isShipmentInProgress, type DeliverySummary } from '../../model/delivery';
import { routes, useNavigateToDriverLogin } from '../../navigation';
import {
    ErrorContent,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SettyMessage,
    TopAppBar
} from '../../ui/components';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';
import { DeliveryListItem } from './DeliveryParts';
import { DriverTabBar } from './DriverTabBar';
import styles from './driver.module.css';

export function DriverShipmentsScreen() {
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const { state, isRefreshing, load, refresh } = useLoad(deliveryRepository.getShipments, 'driver');

    return (
        <Scaffold
            bottomBar={<DriverTabBar current="shipments" />}
            topBar={<TopAppBar title={<span className="type-title-large-em">내 배차</span>} />}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={`${screenStyles['list-padding']} ${styles.stack}`}>
                        {state.data.length === 0 ? (
                            <SettyMessage
                                description="배차 요청에서 마음에 드는 배송을 수락해 보세요."
                                style={{ marginTop: 48 }}
                                title="아직 수락한 배차가 없어요"
                            />
                        ) : (
                            <>
                                <ShipmentSection shipments={state.data.filter((item) => isShipmentInProgress(item.status))} title="진행 중" />
                                <ShipmentSection shipments={state.data.filter((item) => item.status === 'DELIVERED')} title="배송 완료" />
                            </>
                        )}
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}

function ShipmentSection({ title, shipments }: { title: string; shipments: DeliverySummary[] }) {
    const navigate = useNavigate();
    if (shipments.length === 0) return null;
    return (
        <section className={styles.section}>
            <SectionTitle>{title}</SectionTitle>
            <SegmentedList>
                {shipments.map((shipment, index) => (
                    <DeliveryListItem
                        count={shipments.length}
                        delivery={shipment}
                        index={index}
                        key={shipment.deliveryId}
                        onClick={() => navigate(routes.driverShipmentDetail(shipment.deliveryId))}
                        showStatus
                    />
                ))}
            </SegmentedList>
        </section>
    );
}
