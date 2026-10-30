import { useState } from 'react';
import { deliveryRepository } from '../../api/deliveryRepository';
import { shortAddress } from '../../model/delivery';
import { formatWon } from '../../model/furniture';
import { routes, useNavigateBack, useNavigateToDriverLogin } from '../../navigation';
import {
    AlertDialog,
    BackIconButton,
    ErrorContent,
    FlexibleTopAppBar,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SegmentedListItem,
    SettyMessage
} from '../../ui/components';
import { Icon } from '../../ui/Icon';
import { shapeStyle } from '../../ui/shapes';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';
import styles from './driver.module.css';

/** 배송원 내 정보. 정산은 전용 API가 없어 배송 완료한 배차의 배송비를 합산한다. (Expo 배송원 앱과 같은 방식) */
export function DriverMyPageScreen() {
    const navigateBack = useNavigateBack(routes.driverRequests);
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const { state, isRefreshing, load, refresh } = useLoad(deliveryRepository.getShipments, 'driver');
    const [isLogoutDialogVisible, setIsLogoutDialogVisible] = useState(false);
    const delivered = state.status === 'success' ? state.data.filter((item) => item.status === 'DELIVERED') : [];
    const total = delivered.reduce((sum, item) => sum + item.deliveryFee, 0);

    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="내 정보" />}>
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={`${screenStyles['list-padding']} ${styles.stack}`}>
                        <section className={screenStyles.card} style={{ background: 'var(--primary-container)', color: 'var(--on-primary-container)' }}>
                            <div className={screenStyles.card__header}>
                                <div className={screenStyles.card__shape} style={{ ...shapeStyle('cookie9'), background: 'var(--primary)', color: 'var(--on-primary)' }}>
                                    <Icon name="payments" size={28} />
                                </div>
                                <div className={screenStyles.card__lines}>
                                    <p className="type-body-medium-em">배송 완료 수입</p>
                                    <p className="type-headline-small-em">{formatWon(total)}</p>
                                </div>
                            </div>
                            <dl className={styles['income-stats']}>
                                <div className={styles['income-stats__row']}>
                                    <dt className="type-body-medium">완료 건수</dt>
                                    <dd className="type-title-medium-em">{delivered.length}건</dd>
                                </div>
                                <div className={styles['income-stats__row']}>
                                    <dt className="type-body-medium">건당 평균</dt>
                                    <dd className="type-title-medium-em">{formatWon(delivered.length ? Math.round(total / delivered.length) : 0)}</dd>
                                </div>
                            </dl>
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
                        <section className={styles.section}>
                            <SectionTitle>계정</SectionTitle>
                            <SegmentedList>
                                <SegmentedListItem
                                    count={1}
                                    headline={<span className="type-title-medium">로그아웃</span>}
                                    index={0}
                                    leading={(
                                        <span className={styles['menu-shape']} style={shapeStyle('cookie6')}>
                                            <Icon name="logout" size={20} />
                                        </span>
                                    )}
                                    onClick={() => setIsLogoutDialogVisible(true)}
                                />
                            </SegmentedList>
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
