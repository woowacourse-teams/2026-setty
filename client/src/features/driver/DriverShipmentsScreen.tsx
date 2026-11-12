import { useNavigate } from 'react-router';
import { routes, useNavigateBack, useNavigateToDriverLogin } from '../../navigation';
import {
    BackIconButton,
    CenterFill,
    ErrorContent,
    FlexibleTopAppBar,
    LoadingContent,
    LoadingIndicator,
    PullToRefresh,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SettyMessage
} from '../../ui/components';
import screenStyles from '../screens.module.css';
import { DeliveryListItem } from './DeliveryParts';
import { DeliveryLoadMore } from './DeliveryLoadMore';
import { deliveredPageStore, inProgressPageStore } from './deliveryPageStore';
import { useDeliveryPage } from './useDeliveryPage';
import styles from './driver.module.css';

export function DriverShipmentsScreen() {
    const navigateBack = useNavigateBack(routes.driverRequests);
    const progress = useDeliveryPage(inProgressPageStore);
    const delivered = useDeliveryPage(deliveredPageStore);
    const isLoading = progress.state.status === 'loading' && delivered.state.status === 'loading';
    const isEmpty = progress.state.status === 'success' && progress.state.data.length === 0
        && delivered.state.status === 'success' && delivered.state.data.length === 0;

    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="내 배차" />}>
            {isLoading && <LoadingContent />}
            {!isLoading && (
                <PullToRefresh isRefreshing={progress.isRefreshing || delivered.isRefreshing} onRefresh={() => {
                    void progress.refresh();
                    void delivered.refresh();
                }}>
                    <div className={`${screenStyles['list-padding']} ${styles.stack}`}>
                        {isEmpty ? (
                            <CenterFill>
                                <SettyMessage
                                    description="배차 요청에서 마음에 드는 배송을 수락해 보세요."
                                    title="아직 수락한 배차가 없어요"
                                />
                            </CenterFill>
                        ) : (
                            <>
                                <ShipmentSection page={progress} title="진행 중" />
                                <ShipmentSection page={delivered} title="배송 완료" />
                            </>
                        )}
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}

function ShipmentSection({ title, page }: { title: string; page: ReturnType<typeof useDeliveryPage> }) {
    const navigate = useNavigate();
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const { state, nextCursor, isLoadingMore, isRefreshing, hasLoadMoreError, load, loadMore } = page;
    if (state.status === 'success' && state.data.length === 0) return null;
    return (
        <section className={styles.section}>
            <SectionTitle>{title}</SectionTitle>
            {state.status === 'loading' && <LoadingIndicator />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <>
                    <SegmentedList>
                        {state.data.map((shipment, index) => (
                            <DeliveryListItem
                                count={state.data.length}
                                delivery={shipment}
                                index={index}
                                key={shipment.deliveryId}
                                onClick={() => navigate(routes.driverShipmentDetail(shipment.deliveryId))}
                                showStatus
                            />
                        ))}
                    </SegmentedList>
                    <DeliveryLoadMore
                        hasError={hasLoadMoreError}
                        hasMore={nextCursor !== null}
                        isLoading={isLoadingMore || isRefreshing}
                        onLoad={loadMore}
                    />
                </>
            )}
        </section>
    );
}
