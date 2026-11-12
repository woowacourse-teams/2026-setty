import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router';
import { ApiError, tokenStore } from '../../api/http';
import { routes, useNavigateToLogin } from '../../navigation';
import { BackIconButton, Button, IconButton, FurnitureThumbnail, StatusBadge } from '../../ui/components';
import { supportApi, type Conversation, type ConversationListItem, type MessageRequest, type SupportCard, type SupportInput, type SupportTurn, type SupportOrder, type CancellationOutcome } from './supportApi';
import { failureMessage, isMessageSubmitKey, inputLabel, mergeTurns, pageTurns, resolvePending, isUncertainSendError, shouldPollCancellation, mergeCancellationOutcome } from './supportState';
import styles from './support.module.css';
import { formatWon } from '../../model/furniture';
import { orderStatusLabel, orderStatusOrNull, deliveryStatusOf } from '../../model/order';
import { Icon } from '../../ui/Icon';

const formatDate = (date: string | null) => date ? new Date(date).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '주문일 정보 없음';
const reasonLabels: Record<string, string> = {
    BEFORE_DRIVER_ACCEPTANCE: '현재 배차 수락 전으로 취소 요청이 가능해요.',
    CANCELLATION_PENDING: '이미 취소 요청을 처리하고 있어요.',
    ALREADY_CANCELLED: '이미 취소된 주문이에요. 환불 완료를 의미하지는 않아요.',
    PURCHASE_COMPLETED: '구매 확정된 주문이에요.', PAYMENT_PENDING: '결제 대기 중인 주문이에요.',
    ORDER_CONFIRMATION_PENDING: '결제가 승인되어 주문 확정을 기다리고 있어요. 확정 후 취소 가능 여부를 다시 확인해 주세요.',
    ORDER_EXPIRED: '만료된 주문이에요.', DRIVER_ACCEPTED: '배송원이 배차를 수락했어요.',
    ALREADY_PICKED_UP: '이미 픽업이 완료됐어요.', ALREADY_DELIVERED: '이미 배송이 완료됐어요.',
    STATE_NOT_SYNCHRONIZED: '주문과 배송 상태 반영을 확인해야 해요.',
    DELIVERY_LOOKUP_FAILED: '배송 상태를 조회하지 못했어요.', UNKNOWN_ORDER_STATUS: '주문 상태를 확인해야 해요.'
};
const eligibilityLabel = { ELIGIBLE: '현재 취소 요청 가능', INELIGIBLE: '현재 취소 요청 불가', UNKNOWN: '취소 가능 여부 확인 불가' };

export function SupportScreen() {
    const navigate = useNavigate();
    const { conversationId } = useParams();
    const session = useSyncExternalStore(tokenStore.subscribe, tokenStore.get);
    if (!session) return <Navigate replace to={routes.login} />;
    // URL 변경 시 이전 대화의 입력·응답·진행 중 요청을 새 대화에 섞지 않는다.
    return <div className={styles.standalone}><SupportView key={`${session}:${conversationId ?? 'new'}`} conversationId={conversationId ? Number(conversationId) : null} onBack={() => navigate(conversationId ? routes.support : routes.orders)} /></div>;
}
function SupportView({ conversationId, onSelect, onClose, onBack }: { conversationId: number | null; onSelect?: (id: number | null) => void; onClose?: () => void; onBack?: () => void }) {
    const navigate = useNavigate();
    const login = useNavigateToLogin();
    const select = (id: number | null) => onSelect ? onSelect(id) : navigate(id === null ? routes.support : routes.supportConversation(id));
    const currentTurn = useRef<HTMLDivElement>(null);
    const scrollRequest = useRef<string | null>(null);
    const composing = useRef(false);
    const [history, setHistory] = useState<ConversationListItem[]>([]);
    const [historyCursor, setHistoryCursor] = useState<string | null>(null);
    const [conversation, setConversation] = useState<Conversation | null>(null);
    const [turns, setTurns] = useState<SupportTurn[]>([]);
    const [cursor, setCursor] = useState<number | null>(null);
    const [text, setText] = useState('');
    const [loading, setLoading] = useState(true);
    const [sending, setSending] = useState(false);
    const [slow, setSlow] = useState(false);
    const [error, setError] = useState('');
    const [pending, setPending] = useState<MessageRequest | null>(null);
    const [unavailable, setUnavailable] = useState(false);
    const mounted = useRef(true);
    const owner = useRef(tokenStore.get());
    const inFlight = useRef(false);
    const alive = () => mounted.current && owner.current === tokenStore.get();
    const processing = turns.some(turn => turn.status === 'PROCESSING');
    useEffect(() => { if (!sending && !processing) { setSlow(false); return; } const timer = setTimeout(() => setSlow(true), 8000); return () => clearTimeout(timer); }, [sending, processing]);
    const canSubmit = !!conversation && !!text.trim() && !sending && !processing && !pending && !unavailable;
    const fail = (cause: unknown) => {
        if (!alive()) return;
        if (cause instanceof ApiError && (cause.status === 401 || cause.code === 'INVALID_TOKEN')) { login(); return; }
        if (cause instanceof ApiError && [403, 404, 410].includes(cause.status)) { setUnavailable(true); setConversation(null); setTurns([]); }
        setError(cause instanceof Error ? cause.message : '상담 기록을 불러오지 못했어요.');
    };
    const load = async (older = false) => {
        if (conversationId !== null && (!Number.isSafeInteger(conversationId) || conversationId <= 0)) { setUnavailable(true); setError('올바르지 않은 상담 주소예요.'); setLoading(false); return; }
        setLoading(true);
        try {
            if (conversationId === null) {
                const page = await supportApi.list(older ? historyCursor ?? undefined : undefined);
                if (!alive()) return;
                setHistory(previous => older ? [...previous, ...page.items.filter(item => !previous.some(old => old.id === item.id))] : page.items);
                setHistoryCursor(page.nextCursor);
            } else {
                const page = await supportApi.detail(conversationId, older ? cursor ?? undefined : undefined);
                if (!alive()) return;
                setConversation(page.conversation);
                const incoming = pageTurns(page);
                setTurns(previous => mergeTurns(previous, incoming));
                setPending(previous => resolvePending(previous, incoming));
                // 최신 페이지 갱신이 이미 펼친 과거 기록 커서를 되돌리지 않는다.
                if (older || turns.length === 0) setCursor(page.nextCursor);
            }
            setError('');
        } catch (cause) { fail(cause); }
        finally { if (alive()) setLoading(false); }
    };
    useEffect(() => { mounted.current = true; void load(); return () => { mounted.current = false; }; }, []);
    useEffect(() => {
        if (!processing || unavailable || loading) return;
        const timer = setTimeout(() => { void load(); }, 2000);
        return () => clearTimeout(timer);
    }, [turns, loading, unavailable]);
    const create = async () => {
        if (!alive() || inFlight.current) return;
        inFlight.current = true; setSending(true); setError('');
        try { const result = await supportApi.create(); if (alive()) select(result.id); }
        catch (cause) { fail(cause); }
        finally { if (alive()) setSending(false); inFlight.current = false; }
    };
    const send = async (input: SupportInput, retry?: MessageRequest) => {
        if (!alive() || !conversationId || inFlight.current || processing || unavailable) return;
        const body = retry ?? { clientRequestId: crypto.randomUUID(), input };
        inFlight.current = true; setSending(true); setPending(body); setError('');
        scrollRequest.current = body.clientRequestId;
        if (!retry && input.type === 'TEXT') setText('');
        try {
            const result = await supportApi.send(conversationId, body);
            if (!alive()) return;
            setTurns(previous => mergeTurns(previous, [result])); setPending(null);
            void load();
        } catch (cause) {
            if (!alive()) return;
            if (input.type === 'TEXT') setText(current => current || input.text);
            const uncertain = isUncertainSendError(cause);
            if (!uncertain) setPending(null);
            fail(cause);
            // 연결 실패는 접수 실패를 뜻하지 않는다. 서버 기록부터 확인한다.
            if (uncertain || (cause instanceof ApiError && cause.status === 409)) void load();
        } finally { if (alive()) setSending(false); inFlight.current = false; }
    };
    const remove = async () => {
        if (!alive() || !conversationId || inFlight.current || !window.confirm('이 상담 기록을 삭제할까요? 삭제한 기록은 다시 볼 수 없어요.')) return;
        inFlight.current = true; setSending(true);
        try { await supportApi.delete(conversationId); if (alive()) select(null); }
        catch (cause) { fail(cause); }
        finally { if (alive()) setSending(false); inFlight.current = false; }
    };
    useEffect(() => {
        if (!scrollRequest.current) return;
        currentTurn.current?.scrollIntoView({ block: 'start' });
        if (!pending && turns.some(turn => turn.clientRequestId === scrollRequest.current && turn.status !== 'PROCESSING')) scrollRequest.current = null;
    }, [turns, pending]);
    return <div className={`${styles.panel} support-private ph-no-capture ph-no-autocapture ph-no-record`} data-ph-no-autocapture>
        <header className={styles.header}>
            {onBack && <BackIconButton onBack={onBack} />}
            <div className={styles.heading}><h2 id="support-title" className="type-title-medium">취소 상담</h2><span className={styles.meta}>AI가 주문 확인을 도와드려요</span></div>
            <details className={styles.menu}><summary aria-label="상담 관리">⋮</summary><nav aria-label="상담 관리">
                <button type="button" disabled={sending || processing || !!pending} onClick={() => select(null)}>이전 상담</button>
                <button type="button" disabled={sending || processing || !!pending} onClick={() => void create()}>새 상담</button>
                <button type="button" disabled={loading} onClick={() => void load()}>새로고침</button>
                {conversationId && <button type="button" disabled={sending || processing || !!pending} onClick={() => void remove()}>삭제</button>}
            </nav></details>
            {onClose && <IconButton icon="close" label="상담 닫기" onClick={onClose} />}
        </header>
        <div className={styles.messages}>
            {error && <div className={styles.error} role="alert">{error}</div>}
            {loading && <p className={styles.meta} role="status">기록을 확인하고 있어요…</p>}
            {conversationId === null ? <>
                <h3 className="type-title-small">이전 상담</h3>
                <p className={styles.meta}>최근 30일간의 대화를 확인할 수 있어요.</p>
                {!loading && history.length === 0 && <div className={styles.welcome}><Icon name="chat" size={36} /><p>아직 상담 기록이 없어요.</p><Button onClick={() => void create()} disabled={sending}>상담 시작하기</Button></div>}
                <ul className={styles.history}>{history.map(item => <li key={item.id}><button type="button" onClick={() => select(item.id)}>{item.hasMessages === false ? '메시지 없음' : item.title || '새 상담'}<span className={styles.meta}>{formatDate(item.lastActivityAt)}</span></button></li>)}</ul>
                {historyCursor && <Button disabled={loading} onClick={() => void load(true)}>상담 더 보기</Button>}
            </> : <>
                {cursor && <Button variant="text" disabled={loading} onClick={() => void load(true)}>이전 메시지 보기</Button>}
                {!loading && turns.length === 0 && !pending && <div className={styles.welcome}><span className={styles.avatar}><Icon name="chat" size={28} /></span><h3 className="type-title-medium">어떤 주문이 궁금하세요?</h3><p>주문 번호를 몰라도 괜찮아요.<br />어떤 가구인지 말씀해 주시면 함께 찾아드릴게요.</p><div className={styles.examples}>{['최근 주문 보여줘', '가장 최근 주문 취소 가능해?'].map(example => <Button key={example} variant="outlined" disabled={!conversation || sending || processing || !!pending || unavailable} onClick={() => void send({ type: 'TEXT', text: example })}>{example}</Button>)}</div></div>}
                <div role="log" aria-label="상담 내용" aria-live="polite" className={styles.log}>
                    {turns.map(turn => <div className={styles.turn} key={turn.turnId} ref={turn.clientRequestId === scrollRequest.current ? currentTurn : undefined}>
                        <p className={styles.user}>{inputLabel(turn, turns)}</p>
                        {turn.status === 'PROCESSING' && <WaitingBubble slow={slow} />}
                        {turn.status === 'FAILED' && <p className={styles.error}>{failureMessage(turn.errorCode)}</p>}
                        {turn.answer && <p className={styles.answer}><AnswerText text={turn.cards.some(c => c.type === 'ORDERS') ? turn.answer.split('\n\n')[0] : turn.answer} /></p>}
                        {turn.cards.map((card, index) => <Card key={index} card={card} latest={turn.turnId === turns.at(-1)?.turnId} current={turn.turnId === turns.filter(item => item.status === 'SUCCEEDED').at(-1)?.turnId} onCancel={orderId => void send({ type: 'CONFIRM_CANCEL', sourceTurnId: turn.turnId, orderId })} onCheck={orderId => void send({ type: 'TEXT', text: `주문 #${orderId} 현재 취소 상태 확인해줘` })} disabled={sending || processing || !!pending || unavailable} onSelect={orderId => void send({ type: 'SELECT_ORDER', sourceTurnId: turn.turnId, orderId })} onNext={() => void send({ type: 'NEXT_ORDERS', sourceTurnId: turn.turnId })} onDetail={() => onClose?.()} onOther={orderId => void send({ type: 'DECLINE', sourceTurnId: turn.turnId, orderId })} />)}
                    </div>)}
                </div>
                {pending && <div className={styles.turn} ref={currentTurn}><p className={styles.user}>{inputLabel({ input: pending.input } as SupportTurn, turns)}</p>{sending ? <WaitingBubble slow={slow} /> : <p className={styles.answer} role="status">전송 결과를 확인하고 있어요.</p>}{!sending && <Button onClick={() => void send(pending.input, pending)}>같은 요청 다시 확인</Button>}</div>}
            </>}
        </div>
        {conversationId !== null && !unavailable && <form className={styles.form} onSubmit={event => { event.preventDefault(); if (canSubmit && !composing.current) void send({ type: 'TEXT', text: text.trim() }); }}>
            <label className={styles.srOnly} htmlFor="support-message">상담 메시지</label>
            <div className={styles.composer}><textarea id="support-message" rows={2} maxLength={2000} value={text} onChange={event => setText(event.target.value)}
                onCompositionStart={() => { composing.current = true; }} onCompositionEnd={() => { composing.current = false; }}
                onKeyDown={event => {
                    if (!isMessageSubmitKey({ key: event.key, shiftKey: event.shiftKey, isComposing: event.nativeEvent.isComposing, keyCode: event.nativeEvent.keyCode }, composing.current, canSubmit)) return;
                    event.preventDefault(); event.currentTarget.form?.requestSubmit();
                }} aria-describedby="support-input-help" placeholder="주문한 가구, 취소할 수 있나요?" disabled={sending || processing || !!pending} />
            <Button type="submit" disabled={!canSubmit}>보내기</Button></div>
            <span id="support-input-help" className={styles.srOnly}>Enter로 보내기, Shift와 Enter로 줄바꿈</span>
            <p className={styles.disclaimer}>최종 동의 후 취소 요청을 접수해요.</p>
        </form>}
    </div>;
}

/** dialog를 닫아도 패널은 유지해 진행 중 요청과 동일 요청 ID를 잃지 않는다. */
export function OrderSupportWidget() {
    const session = useSyncExternalStore(tokenStore.subscribe, tokenStore.get);
    return session ? <SessionWidget key={session} /> : null;
}
const sessionConversations = new Map<string, number | null>();
function SessionWidget() {
    const login = useNavigateToLogin();
    const dialog = useRef<HTMLDialogElement>(null);
    const [opened, setOpened] = useState(false);
    const sessionToken = tokenStore.get()!;
    const [conversationId, updateConversationId] = useState<number | null | undefined>(() => sessionConversations.get(sessionToken));
    const setConversationId = (id: number | null) => { sessionConversations.set(sessionToken,id); updateConversationId(id); };
    const [error, setError] = useState('');
    const creating = useRef(false);
    const owner = useRef(tokenStore.get());
    const mounted = useRef(true);
    useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
    const prepare = async () => {
        if (creating.current || owner.current !== tokenStore.get()) return;
        creating.current = true; setError('');
        try {
            const conversation = await supportApi.create();
            if (mounted.current && owner.current === tokenStore.get()) setConversationId(conversation.id);
        } catch (cause) {
            if (!mounted.current || owner.current !== tokenStore.get()) return;
            if (cause instanceof ApiError && (cause.status === 401 || cause.code === 'INVALID_TOKEN')) { dialog.current?.close(); login(); return; }
            setError(cause instanceof Error ? cause.message : '상담을 열지 못했어요.');
        }
        finally { creating.current = false; }
    };
    const close = () => { dialog.current?.close(); setOpened(false); };
    const open = () => {
        dialog.current?.showModal(); setOpened(true);
        if (conversationId === undefined) void prepare();
    };
    return <>
        <button className={styles.launcher} type="button" aria-label="주문 취소 상담 열기" aria-haspopup="dialog" aria-expanded={opened} onClick={open}><Icon name="chat" size={26} /><span>취소 상담</span></button>
        <dialog ref={dialog} className={`${styles.dialog} support-private ph-no-capture ph-no-autocapture ph-no-record`} aria-labelledby="support-title" onCancel={close} onClose={() => setOpened(false)}>
            {conversationId === undefined ? <div className={styles.preparing}><header className={styles.header}><h2 id="support-title" className="type-title-medium">취소 상담</h2><IconButton icon="close" label="상담 닫기" onClick={close} /></header>{error ? <div className={styles.messages}><p role="alert">{error}</p><Button onClick={() => void prepare()}>다시 열기</Button></div> : <p className={styles.messages} role="status">상담을 준비하고 있어요…</p>}</div> : <SupportView key={conversationId ?? 'history'} conversationId={conversationId} onSelect={setConversationId} onClose={close} />}
        </dialog>
    </>;
}

function WaitingBubble({ slow }: { slow: boolean }) {
    return <p className={styles.waiting} role="status" aria-label={slow ? undefined : '답변을 기다리고 있어요'}>
        {slow ? '조금 더 걸리고 있어요' : <span className={styles.dots} aria-hidden="true"><i /><i /><i /></span>}
    </p>;
}

function AnswerText({ text }: { text: string }) {
    // Render a small safe subset for historical model replies, without HTML injection.
    return <>{text.split(/(\*\*[^*]+\*\*)/g).map((part,index) => part.startsWith('**') && part.endsWith('**') ? <strong key={index}>{part.slice(2,-2)}</strong> : part)}</>;
}
function Card({ card, disabled, current, latest, onCancel, onCheck, onSelect, onNext, onDetail, onOther }: { card: SupportCard; disabled: boolean; current: boolean; latest: boolean; onCancel: (id: number) => void; onCheck: (id: number) => void; onSelect: (id: number) => void; onNext: () => void; onDetail: () => void; onOther: (id: number) => void }) {
    if (card.type === 'ORDERS') return <div className={styles.orderResults}>
        {card.purpose === 'CANCEL_CHECK' && !card.confirmLatest && card.orders.some(order => order.orderStatus !== 'CANCELLED') && <p className={styles.meta}>주문을 누르면 취소 가능 여부를 확인해요</p>}
        <div className={styles.orderGroup}>{card.orders.map((order, index) => {
            const cancelled = order.orderStatus === 'CANCELLED';
            const content = <><div className={styles.orderSummary}>
                <FurnitureThumbnail size={48} alt={order.title ?? '주문한 가구'} imageUrl={order.thumbnailUrl} placeholder="가구" />
                <div className={styles.orderInfo}>
                    <span className={styles.meta}>주문 #{order.orderId}{order.createdDateKst ? ` · ${order.createdDateKst.slice(5).replace('-', '/')}` : ''}</span>
                    <strong>{!(card.confirmLatest && card.orders.length === 1) && `${index + 1}. `}{order.title ?? '상품 정보 없음'}</strong>
                    {order.totalPrice != null && <span className={styles.orderPrice}>{formatWon(order.totalPrice)}</span>}
                </div>
            </div>
            <StatusBadge containerColor={cancelled || order.orderStatus === 'CANCEL_PENDING' ? 'var(--error-container)' : order.deliveryStatus === 'DELIVERED' ? 'var(--surface-container-highest)' : 'var(--primary-container)'} contentColor={cancelled || order.orderStatus === 'CANCEL_PENDING' ? 'var(--on-error-container)' : order.deliveryStatus === 'DELIVERED' ? 'var(--on-surface-variant)' : 'var(--on-primary-container)'} text={supportOrderStatusLabel(order)} /></>;
            return <article key={order.orderId} className={styles.orderCard}>
                <div className={styles.orderRow}>
                    {cancelled ? <div className={styles.orderMain}>{content}</div> : <button type="button" className={styles.orderMain} disabled={disabled || (card.confirmLatest && !current)} onClick={() => onSelect(order.orderId)} aria-label={`${card.confirmLatest && card.orders.length === 1 ? '' : `${index + 1}번 `}${order.title ?? '상품'} 주문 ${order.orderId} ${card.confirmLatest ? '맞아요' : '취소 가능 여부 확인'}`}>{content}</button>}
                    <Link className={styles.detailLink} to={routes.orderDetail(order.orderId)} onClick={onDetail} aria-label={`${order.title ?? '상품'} 주문 ${order.orderId} 상세 보기`}>상세</Link>
                </div>
                {card.confirmLatest && !cancelled && <div className={styles.orderActions}>
                    <Button variant="text" disabled={disabled || !current} onClick={() => onOther(order.orderId)}>다른 주문이에요</Button>
                    <Button disabled={disabled || !current} onClick={() => onSelect(order.orderId)}>맞아요</Button>
                </div>}
            </article>;
        })}</div>
        {card.hasUndatedOrders && !card.confirmLatest && <p className={styles.meta}>{card.excludedUndatedCount === undefined ? '주문일이 없는 주문은 날짜 검색에 포함되지 않아요.' : `주문일 정보가 없는 주문 ${card.excludedUndatedCount}건은 날짜 검색에서 제외됐어요.`} 상품명으로도 찾아보세요.</p>}
        {card.nextCursor ? <Button variant="outlined" disabled={disabled} onClick={onNext}>주문 {card.pageSize ?? 5}개 더 보기</Button> : card.orders.length > 0 && !card.confirmLatest && <p className={styles.meta}>모든 주문을 확인했어요.</p>}
    </div>;
    if (card.type === 'CANCELLATION_REQUEST') return <CancellationProgress card={card} onDetail={onDetail} />;
    if (card.confirmationOnly) return current ? <div className={styles.card}>
        <CancellationConsent card={card} current={current} disabled={disabled} onCancel={onCancel} onCheck={onCheck} onDecline={() => onOther(card.order.orderId)} />
    </div> : null;
    return <div className={styles.card}>
        <span className={`${styles.eligibility} ${card.eligibility === 'ELIGIBLE' ? styles.eligible : card.eligibility === 'INELIGIBLE' ? styles.ineligible : styles.unknown}`}>{eligibilityLabel[card.eligibility]}</span>
        <p>{reasonLabels[card.reasonCode] ?? '상세 상태를 다시 확인해 주세요.'}</p>
        {(card.eligibility === 'INELIGIBLE' || card.eligibility === 'UNKNOWN') && <p className={styles.meta}>이번 상담으로 주문 상태는 바뀌지 않았어요.</p>}
        <p>{card.order.title ?? '상품 정보 없음'} · 주문 #{card.order.orderId}</p>
        <p className={styles.meta}>확인 시각: {formatDate(card.observedAt)}{!latest && <><br />당시 조회 결과예요. 현재 상태는 다시 질문해 확인하세요.</>}</p>
        <Link to={routes.orderDetail(card.order.orderId)} onClick={onDetail}>주문 상세 보기</Link>
        {card.cancellationOffer && <CancellationConsent card={card} current={current} disabled={disabled} onCancel={onCancel} onCheck={onCheck} onDecline={() => onOther(card.order.orderId)} />}
    </div>;
}

function CancellationConsent({ card, current, disabled, onCancel, onCheck, onDecline }: {
    card: Extract<SupportCard, { type: 'ELIGIBILITY' }>; current: boolean; disabled: boolean;
    onCancel: (id: number) => void; onCheck: (id: number) => void; onDecline: () => void;
}) {
    if (!current) return null;
    const fresh = current && !!card.confirmationExpiresAt && Date.parse(card.confirmationExpiresAt) > Date.now();
    if (!fresh) return <Button variant="text" disabled={disabled} onClick={() => onCheck(card.order.orderId)}>취소 가능 여부 다시 확인</Button>;
    return <section className={styles.consent} aria-label="취소 요청 최종 확인">
        <h3 className="type-title-small">이 주문을 취소하시겠어요?</h3>
        {card.confirmationOnly && <p><strong>{card.order.title ?? '상품 정보 없음'}</strong> · 주문 #{card.order.orderId}</p>}
        <p>동의하면 현재 배송 상태를 다시 확인해 취소를 요청해요.</p>
        <div className={styles.orderActions}>
            <Button variant="text" disabled={disabled} onClick={onDecline}>취소하지 않기</Button>
            <Button disabled={disabled} onClick={() => {
                if (Date.parse(card.confirmationExpiresAt!) <= Date.now()) { onCheck(card.order.orderId); return; }
                onCancel(card.order.orderId);
            }}>취소 요청</Button>
        </div>
    </section>;
}

function CancellationProgress({ card, onDetail }: { card: Extract<SupportCard, {type:'CANCELLATION_REQUEST'}>; onDetail: () => void }) {
    const [result, setResult] = useState<CancellationOutcome | null>(null);
    const [attempt, setAttempt] = useState(0);
    const [waiting, setWaiting] = useState(true);
    const [lookupFailed, setLookupFailed] = useState(false);
    useEffect(() => {
        let alive = true;
        const started = Date.now();
        const owner = tokenStore.get();
        const controller = new AbortController();
        let timer: ReturnType<typeof setTimeout>;
        const deadline = setTimeout(() => { if (alive) { setWaiting(false); controller.abort(); } },30000);
        setWaiting(true);
        setLookupFailed(false);
        const poll = async () => {
            let status: CancellationOutcome['status'] = 'ACCEPTED';
            try {
                const incoming = await supportApi.cancellation(card.requestId,controller.signal);
                if (!alive || owner !== tokenStore.get()) return;
                if (incoming.requestId !== card.requestId || incoming.orderId !== card.order.orderId) throw new Error('Result identity mismatch');
                status = incoming.status;
                setResult(previous => mergeCancellationOutcome(previous,incoming));
                setLookupFailed(false);
            } catch {
                if (!alive || owner !== tokenStore.get()) return;
                // Ending the 30-second observation window is not a failed result lookup.
                if (!controller.signal.aborted) setLookupFailed(true);
            }
            if (alive && !controller.signal.aborted && shouldPollCancellation(status,started,Date.now())) timer=setTimeout(poll,2000);
            else if (alive) { setWaiting(false); clearTimeout(deadline); }
        };
        void poll();
        return () => { alive=false; controller.abort(); clearTimeout(timer); clearTimeout(deadline); };
    },[card.requestId,attempt]);
    const status=result?.status ?? card.previousStatus ?? 'ACCEPTED';
    return <div className={styles.card}>
        <strong role="status">{cancellationStatusLabel(status, card.historical)}</strong>
        <p>{card.order.title ?? '상품 정보 없음'} · 주문 #{card.order.orderId}</p>
        {card.historical && <p className={styles.meta}>접수 시각: {formatDate(card.acceptedAt)}</p>}
        <p>{cancellationDescription(status, result?.reasonCode, lookupFailed, waiting)}</p>
        {status === 'ACCEPTED' && !waiting && <Button onClick={() => setAttempt(n=>n+1)}>처리 결과 다시 확인</Button>}
        <Link to={routes.orderDetail(card.order.orderId)} onClick={onDetail}>주문 내역에서 확인</Link>
    </div>;
}

function orderDateLabel(order: SupportOrder): string {
    if (order.createdDateKst === undefined) return formatDate(order.createdAt);
    return order.createdDateKst ?? '주문일 정보 없음';
}
function supportOrderStatusLabel(order: SupportOrder): string {
    if (order.orderStatus === 'COMPLETED') return '구매 확정';
    const status = orderStatusOrNull(order.orderStatus);
    if (!status) return '주문 상태 확인 필요';
    return orderStatusLabel({ id: order.orderId, listing: null, orderStatus: status,
        deliveryStatus: deliveryStatusOf(order.deliveryStatus ?? 'PENDING') });
}
function cancellationStatusLabel(status: CancellationOutcome['status'], historical?: boolean): string {
    if (historical) {
        const labels = { COMPLETED: '완료', REJECTED: '거절', ACCEPTED: '처리 중' };
        return `이전에 접수한 취소 요청 · ${labels[status]}`;
    }
    const labels = { COMPLETED: '취소가 완료됐어요', REJECTED: '취소 요청이 거절됐어요', ACCEPTED: '취소 처리 중이에요' };
    return labels[status];
}
function cancellationDescription(status: CancellationOutcome['status'], reasonCode: string | null | undefined,
    lookupFailed: boolean, waiting: boolean): string {
    if (status === 'COMPLETED') return '주문 내역에서도 취소를 확인할 수 있어요. 환불 완료 안내는 아니에요.';
    if (status === 'REJECTED') return reasonLabels[reasonCode ?? ''] ?? '배송 진행 상태로 취소가 거절됐어요.';
    if (lookupFailed) return '현재 처리 결과를 확인하지 못했어요. 요청 실패나 취소 거절을 뜻하지는 않아요.';
    if (waiting) return '배송 상태를 확인하고 있어요. 결과가 확인되면 여기에 표시해 드릴게요.';
    return '아직 최종 결과가 확인되지 않았어요. 잠시 후 다시 확인해 주세요.';
}
