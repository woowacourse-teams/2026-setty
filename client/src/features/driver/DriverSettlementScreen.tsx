import { useState } from 'react';
import { deliveryRepository } from '../../api/deliveryRepository';
import { shortAddress } from '../../model/delivery';
import { formatWon } from '../../model/furniture';
import { useNavigateToDriverLogin } from '../../navigation';
import {
    AlertDialog,
    ErrorContent,
    IconButton,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SegmentedListItem,
    SettyMessage,
    TopAppBar
} from '../../ui/components';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';
import { DriverTabBar } from './DriverTabBar';
import styles from './driver.module.css';

/** 정산. 전용 API가 없어 배송 완료한 배차의 배송비를 합산한다. (Expo 배송원 앱과 같은 방식) */
export function DriverSettlementScreen() {
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const { state, isRefreshing, load, refresh } = useLoad(deliveryRepository.getShipments, 'driver');
    const [isLogoutDialogVisible, setIsLogoutDialogVisible] = useState(false);
    const delivered = state.status === 'success' ? state.data.filter((item) => item.status === 'DELIVERED') : [];
    const total = delivered.reduce((sum, item) => sum + item.deliveryFee, 0);

    return (
        <Scaffold
            bottomBar={<DriverTabBar current="settlement" />}
            topBar={(
                <TopAppBar
                    actions={<IconButton icon="logout" label="로그아웃" onClick={() => setIsLogoutDialogVisible(true)} />}
                    title={<span className="type-title-large-em">정산</span>}
                />
            )}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={`${screenStyles['list-padding']} ${styles.stack}`}>
                        <section className={screenStyles.card} style={{ background: 'var(--primary-container)', color: 'var(--on-primary-container)' }}>
                            <div className={styles.hero}>
                                <p className="type-label-large">배송 완료 수입</p>
                                <p className="type-display-small-em">{formatWon(total)}</p>
                            </div>
                            <div className={styles.summary}>
                                <div className={styles.summary__item}>
                                    <p className="type-label-medium">완료 건수</p>
                                    <p className="type-title-large-em">{delivered.length}건</p>
                                </div>
                                <div className={styles.summary__item}>
                                    <p className="type-label-medium">건당 평균</p>
                                    <p className="type-title-large-em">{formatWon(delivered.length ? Math.round(total / delivered.length) : 0)}</p>
                                </div>
                            </div>
                        </section>
                        <section className={styles.section}>
                            <SectionTitle>완료한 배송</SectionTitle>
                            {delivered.length === 0 ? (
                                <SettyMessage description="배송을 완료하면 이곳에 쌓여요." title="완료한 배송이 아직 없어요" />
                            ) : (
                                <SegmentedList>
                                    {delivered.map((item, index) => (
                                        <SegmentedListItem
                                            count={delivered.length}
                                            headline={<span className="type-title-medium">{item.itemName}</span>}
                                            index={index}
                                            key={item.deliveryId}
                                            supporting={<span className={`${styles.route} type-body-medium`}>{shortAddress(item.deliveryAddress)}</span>}
                                            trailing={<span className={`${styles.fee} type-title-medium-em`}>+{formatWon(item.deliveryFee)}</span>}
                                        />
                                    ))}
                                </SegmentedList>
                            )}
                        </section>
                    </div>
                </PullToRefresh>
            )}
            {isLogoutDialogVisible && (
                <AlertDialog
                    confirmLabel="로그아웃"
                    dismissLabel="취소"
                    onConfirm={() => {
                        setIsLogoutDialogVisible(false);
                        navigateToDriverLogin();
                    }}
                    onDismiss={() => setIsLogoutDialogVisible(false)}
                    text="다시 배차를 받으려면 로그인해야 합니다."
                    title="로그아웃할까요?"
                />
            )}
        </Scaffold>
    );
}
