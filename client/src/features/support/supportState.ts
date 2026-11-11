import { ApiError } from '../../api/http';
import { UncertainSupportResponse } from './supportApi';
import type { SupportTurn, MessageRequest, TurnPage, CancellationOutcome } from './supportApi';

/** HTTP 응답과 재조회가 겹쳐도 같은 턴을 한 번만, 서버 ID 순서로 표시한다. */
export function mergeTurns(previous: SupportTurn[], incoming: SupportTurn[]): SupportTurn[] {
    const byId = new Map(previous.map(turn => [turn.turnId, turn]));
    for (const turn of incoming) {
        const current = byId.get(turn.turnId);
        // 늦게 도착한 PROCESSING 응답이 이미 받은 최종 결과를 되돌리지 않는다.
        if (current && current.status !== 'PROCESSING' && turn.status === 'PROCESSING') continue;
        byId.set(turn.turnId, turn);
    }
    return [...byId.values()].sort((a, b) => a.turnId - b.turnId);
}
export function pageTurns(page: TurnPage): SupportTurn[] { return page.activeTurn ? [...page.items, page.activeTurn] : page.items; }
export function resolvePending(pending: MessageRequest | null, turns: SupportTurn[]): MessageRequest | null {
    return pending && !turns.some(turn => turn.clientRequestId === pending.clientRequestId) ? pending : null;
}
export function inputLabel(turn: SupportTurn, turns: SupportTurn[] = []): string {
    const input = turn.input;
    if (input.type === 'TEXT') return input.text;
    if (input.type === 'NEXT_ORDERS') return '주문 더 보기';
    if (input.type === 'CONFIRM_CANCEL') return '취소 요청';
    const source = turns.find(item => item.turnId === input.sourceTurnId);
    if (input.type === 'DECLINE') return source?.cards.some(card => card.type === 'ELIGIBILITY') ? '취소하지 않기' : '다른 주문이에요';
    const group = source?.cards.find(card => card.type === 'ORDERS' && card.orders.some(order => order.orderId === input.orderId));
    if (group?.type === 'ORDERS' && group.confirmLatest) return '맞아요';
    const index = group?.type === 'ORDERS' ? group.orders.findIndex(order => order.orderId === input.orderId) : -1;
    return index >= 0 ? `${index + 1}번 주문(#${input.orderId}) 취소 가능 여부 확인` : `주문 #${input.orderId} 취소 가능 여부 확인`;
}

/** 5xx는 DB 접수 후 발생할 수 있다. 명시적인 접수 전 비활성화만 확정 실패다. */
export function isUncertainSendError(error: unknown): boolean {
    return error instanceof UncertainSupportResponse || (error instanceof ApiError && error.status >= 500 && error.code !== 'AI_DISABLED');
}

export function failureMessage(code: string | null): string {
    if (code === 'LOCAL_MODEL_BUDGET_EXHAUSTED') return '테스트 호출 한도로 이 답변을 완료하지 못했어요.';
    return '이번 답변을 완료하지 못했어요. 주문은 변경하지 않았어요. 잠시 후 다시 물어봐 주세요.';
}

/** IME의 확정 Enter와 줄바꿈은 전송 단축키가 아니다. */
export function isMessageSubmitKey(event: { key: string; shiftKey: boolean; isComposing: boolean; keyCode: number }, composing: boolean, ready: boolean): boolean {
    return ready && event.key === 'Enter' && !event.shiftKey && !composing && !event.isComposing && event.keyCode !== 229;
}


export function shouldPollCancellation(status: CancellationOutcome['status'], startedAt: number, now: number): boolean {
    return status === 'ACCEPTED' && now - startedAt < 30000;
}
export function mergeCancellationOutcome(previous: CancellationOutcome | null, incoming: CancellationOutcome): CancellationOutcome {
    if (!previous) return incoming;
    if (previous.requestId !== incoming.requestId || previous.orderId !== incoming.orderId || previous.status !== 'ACCEPTED') return previous;
    return incoming;
}
