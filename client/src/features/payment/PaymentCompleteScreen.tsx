import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { furnitureRepository } from '../../api/repositories';
import { formatWon } from '../../model/furniture';
import { routes } from '../../navigation';
import { BackIconButton, BottomBar, Button, Scaffold, SettyMessage, TopAppBar } from '../../ui/components';
import screenStyles from '../screens.module.css';

/** 결제 화면이 승인 응답으로 넘겨주는 값. 주문·배송 상태는 서버가 뒤이어 바꾸므로 여기서는 읽지 않는다. */
export type PaymentCompleteState = {
    orderId: number;
    amount: number;
    listingId: number;
};

function isCompleteState(value: unknown): value is PaymentCompleteState {
    return typeof value === 'object' && value !== null
        && Number.isSafeInteger((value as PaymentCompleteState).orderId)
        && Number.isFinite((value as PaymentCompleteState).amount)
        && Number.isSafeInteger((value as PaymentCompleteState).listingId);
}

/**
 * 결제 완료 화면. 승인 응답에 있는 주문 번호와 금액만으로 그린다.
 * 주문 확정과 배송 요청은 승인 뒤 서버가 이벤트로 처리하므로, 그 상태는 주문 상세에서 본다.
 */
export function PaymentCompleteScreen() {
    const location = useLocation();
    const navigate = useNavigate();
    const state = location.state as unknown;
    const complete = isCompleteState(state) ? state : null;
    const listingId = complete?.listingId ?? null;
    const [title, setTitle] = useState<string | null>(null);

    useEffect(() => {
        if (listingId === null) return;
        // 매물 제목은 결제로 바뀌지 않는 값이라 상세에서 읽어도 안전하다. 실패해도 화면은 그대로 보여 준다
        furnitureRepository.getFurnitureDetail(listingId)
            .then((listing) => setTitle(listing.title))
            .catch(() => undefined);
    }, [listingId]);

    if (!complete) return <Navigate replace to={routes.orders} />;

    const goHome = () => navigate(routes.home, { replace: true });
    return (
        <Scaffold
            bottomBar={(
                <BottomBar>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, width: '100%' }}>
                        <Button fullWidth onClick={() => navigate(routes.orderDetail(complete.orderId), { replace: true })} size="medium">
                            주문 상세 보기
                        </Button>
                        <Button fullWidth onClick={goHome} size="medium" variant="tonal">홈으로</Button>
                    </div>
                </BottomBar>
            )}
            topBar={(
                <TopAppBar
                    navigation={<BackIconButton onBack={goHome} />}
                    title={<span className="type-title-large-em" style={{ fontWeight: 400 }}>결제 완료</span>}
                />
            )}
        >
            <div className={screenStyles['list-padding']} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <SettyMessage
                    description="배송 기사를 찾으면 주문 내역에서 알려 드려요"
                    shape="cookie9"
                    shapeColor="var(--primary-container)"
                    title="결제가 완료되었습니다"
                />
                <div className={screenStyles['total-row']}>
                    <span style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
                        <span className="type-title-medium-em">총 결제 금액</span>
                        {title && <span className="type-body-medium" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{title}</span>}
                    </span>
                    <span className={`${screenStyles['total-row__amount']} type-headline-small-em`}>{formatWon(complete.amount)}</span>
                </div>
            </div>
        </Scaffold>
    );
}
