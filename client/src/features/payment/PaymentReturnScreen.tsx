import { useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ApiError, NetworkError } from '../../api/http';
import { paymentRepository } from '../../api/repositories';
import { routes } from '../../navigation';
import { Button, CenterFill, LoadingIndicator } from '../../ui/components';
import { PaymentScaffold } from './PaymentScreen';
import { clearPendingPayment, PAY_PROCESS_CANCELED, readPendingPayment } from './pendingPayment';

const CONFIRM_MAX_ATTEMPTS = 3;
const CONFIRM_RETRY_DELAY_MILLIS = 1_000;

function confirmErrorMessage(error: unknown): string {
    if (error instanceof ApiError) return error.message;
    if (error instanceof NetworkError) return '결제 확인 중 네트워크 에러가 발생했습니다. 주문 내역을 확인해 주세요';
    return '결제를 확인하지 못했습니다';
}

async function confirmWithRetry(paymentKey: string, tossOrderId: string, amount: number) {
    for (let attempt = 1; attempt < CONFIRM_MAX_ATTEMPTS; attempt += 1) {
        try {
            return await paymentRepository.confirm(paymentKey, tossOrderId, amount);
        } catch (error) {
            if (!(error instanceof NetworkError)) throw error;
            await new Promise((resolve) => setTimeout(resolve, CONFIRM_RETRY_DELAY_MILLIS));
        }
    }
    return paymentRepository.confirm(paymentKey, tossOrderId, amount);
}

/**
 * 토스 결제창이 성공·실패 후 돌려보내는 화면.
 * 성공이면 서버 승인을 요청하고, 실패면 PENDING 주문을 정리한 뒤 결제 화면이나 매물 상세로 돌아간다.
 */
export function PaymentReturnScreen({ result }: { result: 'success' | 'fail' }) {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const isHandled = useRef(false);
    const listingId = Number(searchParams.get('listingId'));

    useEffect(() => {
        if (isHandled.current) return;
        isHandled.current = true;
        const pending = readPendingPayment();
        const backToPayment = (failedMessage: string | null) => {
            if (Number.isSafeInteger(listingId) && listingId > 0) {
                navigate(routes.payment(listingId), { replace: true, state: { failedMessage } });
            } else {
                navigate(routes.home, { replace: true });
            }
        };

        if (result === 'fail') {
            const tossOrderId = searchParams.get('orderId') ?? searchParams.get('fallbackOrderId');
            // 실패 정리는 최선 노력이다. 실패해도 서버가 PENDING 주문을 만료 시간에 정리한다
            if (tossOrderId) void paymentRepository.fail(tossOrderId).catch(() => undefined);
            clearPendingPayment();
            const code = searchParams.get('code');
            // 사용자가 결제창을 직접 닫은 경우는 에러로 보여주지 않는다
            backToPayment(code === PAY_PROCESS_CANCELED ? null : searchParams.get('message') ?? '결제를 진행하지 못했습니다');
            return;
        }

        const paymentKey = searchParams.get('paymentKey');
        const tossOrderId = searchParams.get('orderId');
        if (!paymentKey || !tossOrderId) {
            backToPayment('결제 결과를 확인하지 못했습니다');
            return;
        }
        // 서버 승인 요청에는 주문 생성 응답의 총액을 보낸다
        const amount = pending?.tossOrderId === tossOrderId ? pending.amount : Number(searchParams.get('amount'));
        confirmWithRetry(paymentKey, tossOrderId, amount)
            .then(() => {
                clearPendingPayment();
                // 결제로 매물 상태가 바뀌었으므로 매물 상세를 새로 불러온다
                if (Number.isSafeInteger(listingId) && listingId > 0) {
                    navigate(routes.furnitureDetail(listingId), { replace: true, state: { paymentCompleted: true } });
                } else {
                    navigate(routes.orders, { replace: true });
                }
            })
            .catch((error: unknown) => {
                // 서버가 승인을 거절한 경우에만 주문을 정리한다.
                // 응답을 받지 못한 경우는 서버에서 승인이 진행됐을 수 있어 실패 처리하면 결제와 주문이 어긋난다.
                if (error instanceof ApiError) void paymentRepository.fail(tossOrderId).catch(() => undefined);
                clearPendingPayment();
                backToPayment(confirmErrorMessage(error));
            });
    }, [listingId, navigate, result, searchParams]);

    // 승인 중에 화면을 벗어나면 결과를 알릴 수 없으므로 뒤로 가기를 막는다
    return (
        <PaymentScaffold
            bottomBar={<Button disabled fullWidth size="medium">{result === 'success' ? '결제 확인 중' : '결제 진행 중'}</Button>}
            canLeave={false}
            onBack={() => undefined}
        >
            <CenterFill><LoadingIndicator /></CenterFill>
        </PaymentScaffold>
    );
}
