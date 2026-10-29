import { useNavigate } from 'react-router';
import { deliveryRepository } from '../../api/deliveryRepository';
import { isShipmentInProgress, type DeliverySummary } from '../../model/delivery';
import { routes, useNavigateBack, useNavigateToDriverLogin } from '../../navigation';
import {
    BackIconButton,
    CenterFill,
    ErrorContent,
    FlexibleTopAppBar,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SettyMessage
} from '../../ui/components';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';
import { DeliveryListItem } from './DeliveryParts';
import styles from './driver.module.css';

export function DriverShipmentsScreen() {
    const navigateBack = useNavigateBack(routes.driverRequests);
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const { state, isRefreshing, load, refresh } = useLoad(deliveryRepository.getShipments, 'driver');

    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="내 배차" />}>
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={`${screenStyles['list-padding']} ${styles.stack}`}>
                        {state.data.length === 0 ? (
                            <CenterFill>
                                <SettyMessage
                                    description="배차 요청에서 마음에 드는 배송을 수락해 보세요."
                                    title="아직 수락한 배차가 없어요"
                                />
                            </CenterFill>
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
