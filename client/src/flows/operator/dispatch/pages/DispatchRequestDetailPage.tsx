import {
  FormEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '@/shared/api/http';
import {
  completeDispatch,
  getOperatorDispatchRequest,
  OperatorDispatchRequestDetail,
  saveFinalAmount,
  saveMessage,
} from '@/flows/operator/dispatch/api/operatorDispatchApi';
import {
  formatAmount,
  formatKoreanDateTime,
  formatOptionalText,
  getDispatchStatusLabel,
} from '@/flows/operator/dispatch/presentation';
import styles from './OperatorDispatchPages.module.css';

type DetailState = 'loading' | 'ready' | 'not-found' | 'error';
type CopyState = 'idle' | 'copied' | 'error';

interface FinalAmountErrors {
  finalQuotedAmount?: string;
}

const MAX_INTEGER_AMOUNT = 2_147_483_647;

export default function DispatchRequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [request, setRequest] = useState<OperatorDispatchRequestDetail | null>(null);
  const [detailState, setDetailState] = useState<DetailState>('loading');
  const [loadedRequestId, setLoadedRequestId] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const [sellerCopyState, setSellerCopyState] = useState<CopyState>('idle');
  const [buyerCopyState, setBuyerCopyState] = useState<CopyState>('idle');
  const [finalQuotedAmount, setFinalQuotedAmount] = useState('');
  const [messageContent, setMessageContent] = useState('');
  const [finalAmountErrors, setFinalAmountErrors] = useState<FinalAmountErrors>({});
  const [amountSaveError, setAmountSaveError] = useState('');
  const [amountSaveSuccess, setAmountSaveSuccess] = useState('');
  const [isSavingAmount, setIsSavingAmount] = useState(false);
  const [messageError, setMessageError] = useState('');
  const [messageSaveError, setMessageSaveError] = useState('');
  const [messageSaveSuccess, setMessageSaveSuccess] = useState('');
  const [isSavingMessage, setIsSavingMessage] = useState(false);
  const [completionError, setCompletionError] = useState('');
  const [completionSuccess, setCompletionSuccess] = useState('');
  const [isCompleting, setIsCompleting] = useState(false);
  const activeRouteIdRef = useRef(id);
  const isMutating = isSavingAmount || isSavingMessage || isCompleting;

  useLayoutEffect(() => {
    activeRouteIdRef.current = id;
  }, [id]);

  const moveToLogin = useCallback(() => {
    navigate('/operator/login', {
      replace: true,
      state: { from: `${location.pathname}${location.search}` },
    });
  }, [location.pathname, location.search, navigate]);

  useEffect(() => {
    if (!id) {
      return undefined;
    }

    const controller = new AbortController();

    void getOperatorDispatchRequest(id, controller.signal)
      .then((response) => {
        if (activeRouteIdRef.current !== id) return;

        setSellerCopyState('idle');
        setBuyerCopyState('idle');
        setRequest(response);
        setFinalQuotedAmount(response.finalQuotedAmount?.toString() ?? '');
        setMessageContent(response.messageContent ?? '');
        setFinalAmountErrors({});
        setAmountSaveError('');
        setAmountSaveSuccess('');
        setIsSavingAmount(false);
        setMessageError('');
        setMessageSaveError('');
        setMessageSaveSuccess('');
        setIsSavingMessage(false);
        setCompletionError('');
        setCompletionSuccess('');
        setIsCompleting(false);
        setLoadedRequestId(id);
        setDetailState('ready');
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || activeRouteIdRef.current !== id) return;
        if (error instanceof ApiError && error.status === 401) {
          moveToLogin();
          return;
        }
        setLoadedRequestId(id);
        setDetailState(
          error instanceof ApiError && error.status === 404 ? 'not-found' : 'error',
        );
      });

    return () => controller.abort();
  }, [id, moveToLogin, retryKey]);

  const copyUrl = async (url: string, target: 'seller' | 'buyer') => {
    const updateCopyState = target === 'seller' ? setSellerCopyState : setBuyerCopyState;
    updateCopyState('idle');

    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error('Clipboard API is unavailable.');
      }
      await navigator.clipboard.writeText(url);
      updateCopyState('copied');
    } catch {
      updateCopyState('error');
    }
  };

  const validateFinalAmount = (): FinalAmountErrors => {
    const errors: FinalAmountErrors = {};
    const amount = finalQuotedAmount.trim();

    if (!amount) {
      errors.finalQuotedAmount = '최종 금액을 입력해 주세요.';
    } else if (!/^\d+$/.test(amount) || Number(amount) > MAX_INTEGER_AMOUNT) {
      errors.finalQuotedAmount = '0원 이상의 정수 금액을 입력해 주세요.';
    }

    return errors;
  };

  const handleFinalAmountSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!id || !request || request.id !== Number(id) || isMutating) return;

    const errors = validateFinalAmount();
    if (Object.keys(errors).length > 0) {
      setFinalAmountErrors(errors);
      return;
    }

    const savedFinalQuotedAmount = Number(finalQuotedAmount.trim());
    setIsSavingAmount(true);
    setFinalAmountErrors({});
    setAmountSaveError('');
    setAmountSaveSuccess('');

    try {
      const response = await saveFinalAmount(id, {
        finalQuotedAmount: savedFinalQuotedAmount,
      });
      if (activeRouteIdRef.current !== id) return;

      setRequest((current) =>
        current?.id === Number(id)
          ? {
              ...current,
              status: 'FINAL_AMOUNT_CONFIRM_PENDING',
              finalQuotedAmount: savedFinalQuotedAmount,
              buyerConfirmUrl: response.buyerConfirmUrl,
            }
          : current,
      );
      setFinalQuotedAmount(savedFinalQuotedAmount.toString());
      setBuyerCopyState('idle');
      setAmountSaveSuccess('최종 금액을 저장했습니다.');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        moveToLogin();
      } else if (activeRouteIdRef.current !== id) {
        return;
      } else if (error instanceof ApiError && error.status === 409) {
        setAmountSaveError(
          '요청 상태가 변경되어 최종 금액을 저장하지 못했습니다. 입력 내용을 유지했으니 새로고침 후 확인해 주세요.',
        );
      } else if (error instanceof ApiError && error.status === 400 && error.fieldErrors) {
        setFinalAmountErrors({
          finalQuotedAmount: error.fieldErrors.finalQuotedAmount,
        });
      } else {
        setAmountSaveError('최종 금액을 저장하지 못했습니다. 다시 시도해 주세요.');
      }
    } finally {
      if (activeRouteIdRef.current === id) {
        setIsSavingAmount(false);
      }
    }
  };

  const handleMessageSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!id || !request || request.id !== Number(id) || isMutating) return;

    const savedMessageContent = messageContent.trim();
    if (!savedMessageContent) {
      setMessageError('문자 안내 및 상황 기록을 입력해 주세요.');
      return;
    }

    setIsSavingMessage(true);
    setMessageError('');
    setMessageSaveError('');
    setMessageSaveSuccess('');

    try {
      await saveMessage(id, { messageContent: savedMessageContent });
      if (activeRouteIdRef.current !== id) return;

      setRequest((current) =>
        current?.id === Number(id)
          ? { ...current, messageContent: savedMessageContent }
          : current,
      );
      setMessageContent(savedMessageContent);
      setMessageSaveSuccess('메시지 기록을 저장했습니다.');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        moveToLogin();
      } else if (activeRouteIdRef.current !== id) {
        return;
      } else if (error instanceof ApiError && error.status === 400 && error.fieldErrors) {
        setMessageError(error.fieldErrors.messageContent ?? '메시지를 확인해 주세요.');
      } else {
        setMessageSaveError('메시지 기록을 저장하지 못했습니다. 다시 시도해 주세요.');
      }
    } finally {
      if (activeRouteIdRef.current === id) {
        setIsSavingMessage(false);
      }
    }
  };

  const handleCompleteDispatch = async () => {
    if (!id || !request || request.id !== Number(id) || isMutating) return;

    setIsCompleting(true);
    setCompletionError('');
    setCompletionSuccess('');

    try {
      await completeDispatch(id);
      if (activeRouteIdRef.current !== id) return;

      setRequest((current) =>
        current?.id === Number(id)
          ? { ...current, status: 'DISPATCH_COMPLETED' }
          : current,
      );
      setCompletionSuccess('배차 완료 상태로 변경했습니다.');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        moveToLogin();
      } else if (activeRouteIdRef.current !== id) {
        return;
      } else if (error instanceof ApiError && error.status === 409) {
        setCompletionError(
          '요청 상태가 변경되어 배차 완료로 처리하지 못했습니다. 새로고침 후 확인해 주세요.',
        );
      } else {
        setCompletionError('배차 완료 상태로 변경하지 못했습니다. 다시 시도해 주세요.');
      }
    } finally {
      if (activeRouteIdRef.current === id) {
        setIsCompleting(false);
      }
    }
  };

  if (!id) {
    return (
      <div className={styles.stateCard} role="alert">
        <h1>배차 요청을 찾을 수 없어요</h1>
        <Link to="/operator/dispatch-requests">목록으로 돌아가기</Link>
      </div>
    );
  }

  if (loadedRequestId !== id || detailState === 'loading') {
    return (
      <div className={styles.stateCard} role="status">
        배차 요청을 불러오고 있어요…
      </div>
    );
  }

  if (detailState === 'not-found') {
    return (
      <div className={styles.stateCard} role="alert">
        <h1>배차 요청을 찾을 수 없어요</h1>
        <Link to="/operator/dispatch-requests">목록으로 돌아가기</Link>
      </div>
    );
  }

  if (detailState === 'error' || !request) {
    return (
      <div className={styles.stateCard} role="alert">
        <h1>배차 요청을 불러오지 못했어요</h1>
        <p>서버 연결을 확인한 뒤 다시 시도해 주세요.</p>
        <button
          type="button"
          onClick={() => {
            setDetailState('loading');
            setRetryKey((current) => current + 1);
          }}
        >
          다시 시도
        </button>
      </div>
    );
  }

  const canEditFinalAmount =
    request.status === 'FINAL_REVIEW_PENDING' ||
    request.status === 'FINAL_AMOUNT_CONFIRM_PENDING';
  const isAfterBuyerApproval =
    request.status === 'DISPATCH_PENDING' ||
    request.status === 'DISPATCH_COMPLETED' ||
    request.status === 'IN_TRANSIT' ||
    request.status === 'DELIVERY_COMPLETED';

  return (
    <section aria-labelledby="operator-dispatch-detail-title">
      <Link className={styles.backLink} to="/operator/dispatch-requests">
        ← 배차 목록
      </Link>

      <header className={styles.detailHeader}>
        <div>
          <p>배차 요청 #{request.id}</p>
          <h1 id="operator-dispatch-detail-title">배차 요청 상세</h1>
        </div>
        <span className={styles.status} data-status={request.status}>
          {getDispatchStatusLabel(request.status)}
        </span>
      </header>

      <div className={styles.detailGrid}>
        <article className={styles.infoCard}>
          <h2>요청 정보</h2>
          <dl>
            <div>
              <dt>물품 종류</dt>
              <dd>{request.itemType}</dd>
            </div>
            <div>
              <dt>50만 원 초과</dt>
              <dd>{request.highValueItem ? '예' : '아니요'}</dd>
            </div>
            <div>
              <dt>접수 시각</dt>
              <dd>{formatKoreanDateTime(request.createdAt)}</dd>
            </div>
            <div>
              <dt>연결된 예상 견적</dt>
              <dd>
                {request.estimateRequestId === null ? (
                  '없음'
                ) : (
                  <Link to={`/operator/estimate-requests/${request.estimateRequestId}`}>
                    #{request.estimateRequestId}
                  </Link>
                )}
              </dd>
            </div>
          </dl>
        </article>

        <article className={styles.infoCard}>
          <h2>구매자 정보</h2>
          <dl>
            <div>
              <dt>이름</dt>
              <dd>{request.buyer.name}</dd>
            </div>
            <div>
              <dt>연락처</dt>
              <dd>{request.buyer.phoneNumber}</dd>
            </div>
            <div className={styles.fullWidthDefinition}>
              <dt>수령 주소</dt>
              <dd>{request.buyer.deliveryAddress}</dd>
            </div>
          </dl>
        </article>

        <article className={styles.infoCard}>
          <h2>판매자 정보</h2>
          {request.seller === null ? (
            <div className={styles.sellerPending}>
              <strong>판매자 입력 대기</strong>
              <p>판매자가 아직 픽업 정보를 제출하지 않았습니다.</p>
            </div>
          ) : (
            <dl>
              <div>
                <dt>이름</dt>
                <dd>{request.seller.name}</dd>
              </div>
              <div>
                <dt>연락처</dt>
                <dd>{request.seller.phoneNumber}</dd>
              </div>
              <div className={styles.fullWidthDefinition}>
                <dt>픽업 주소</dt>
                <dd>{request.seller.pickupAddress}</dd>
              </div>
              <div>
                <dt>픽업 가능 시간</dt>
                <dd>{request.seller.availablePickupTime}</dd>
              </div>
              <div>
                <dt>입력 완료 시각</dt>
                <dd>{formatKoreanDateTime(request.sellerInputCompletedAt)}</dd>
              </div>
            </dl>
          )}

          <div className={styles.linkSection}>
            <label htmlFor="seller-input-url">판매자 입력 링크</label>
            {request.sellerInputUrl ? (
              <div className={styles.linkControls}>
                <input
                  id="seller-input-url"
                  readOnly
                  value={request.sellerInputUrl}
                  onFocus={(event) => event.currentTarget.select()}
                />
                <button
                  aria-label="판매자 입력 링크 복사"
                  type="button"
                  onClick={() => void copyUrl(request.sellerInputUrl!, 'seller')}
                >
                  {sellerCopyState === 'copied' ? '복사됨' : '링크 복사'}
                </button>
              </div>
            ) : (
              <p className={styles.missingValue}>발급된 판매자 입력 링크가 없습니다.</p>
            )}
            {sellerCopyState === 'copied' && (
              <p className={styles.copySuccess} role="status">
                판매자 입력 링크를 복사했습니다.
              </p>
            )}
            {sellerCopyState === 'error' && (
              <p className={styles.copyError} role="alert">
                링크를 복사하지 못했습니다. 링크를 직접 선택해 복사해 주세요.
              </p>
            )}
          </div>
        </article>

        <article className={styles.infoCard}>
          <h2>운영 기록</h2>

          <div className={styles.recordSection}>
            <h3>최종 금액</h3>
            {canEditFinalAmount ? (
              <form
                className={styles.operationForm}
                noValidate
                onSubmit={handleFinalAmountSubmit}
              >
                <div className={styles.fieldGroup}>
                  <label htmlFor="final-quoted-amount">최종 금액</label>
                  <div className={styles.amountField}>
                    <input
                      aria-describedby={
                        finalAmountErrors.finalQuotedAmount
                          ? 'final-quoted-amount-error'
                          : undefined
                      }
                      aria-invalid={Boolean(finalAmountErrors.finalQuotedAmount)}
                      disabled={isMutating}
                      id="final-quoted-amount"
                      inputMode="numeric"
                      max={MAX_INTEGER_AMOUNT}
                      min="0"
                      name="finalQuotedAmount"
                      required
                      step="1"
                      type="number"
                      value={finalQuotedAmount}
                      onChange={(event) => {
                        setFinalQuotedAmount(event.target.value);
                        setAmountSaveError('');
                        setAmountSaveSuccess('');
                        setFinalAmountErrors((current) => ({
                          ...current,
                          finalQuotedAmount: undefined,
                        }));
                      }}
                    />
                    <span>원</span>
                  </div>
                  {finalAmountErrors.finalQuotedAmount && (
                    <small id="final-quoted-amount-error" role="alert">
                      {finalAmountErrors.finalQuotedAmount}
                    </small>
                  )}
                </div>

                {amountSaveError && (
                  <div className={styles.submitError} role="alert">
                    {amountSaveError}
                  </div>
                )}
                {amountSaveSuccess && (
                  <p className={styles.submitSuccess} role="status">
                    {amountSaveSuccess}
                  </p>
                )}

                <button disabled={isMutating} type="submit">
                  {isSavingAmount ? '저장하고 있어요…' : '최종 금액 저장'}
                </button>
              </form>
            ) : (
              <>
                <p className={styles.readOnlyNotice}>
                  {request.status === 'SELLER_INPUT_PENDING'
                    ? '판매자 입력이 완료되면 최종 금액을 저장할 수 있습니다.'
                    : isAfterBuyerApproval
                      ? '구매자 승인 이후에는 합의된 최종 금액을 변경하지 않습니다.'
                      : '현재 상태에서는 최종 금액을 변경할 수 없습니다.'}
                </p>
                <dl>
                  <div>
                    <dt>최종 금액</dt>
                    <dd>{formatAmount(request.finalQuotedAmount)}</dd>
                  </div>
                  <div>
                    <dt>구매자 금액 확인 시각</dt>
                    <dd>{formatKoreanDateTime(request.amountCheckedAt)}</dd>
                  </div>
                  <div className={styles.fullWidthDefinition}>
                    <dt>내부 운영 메모</dt>
                    <dd>{formatOptionalText(request.operatorNote)}</dd>
                  </div>
                  <div className={styles.fullWidthDefinition}>
                    <dt>종료 사유</dt>
                    <dd>{formatOptionalText(request.closedReason)}</dd>
                  </div>
                </dl>
              </>
            )}
          </div>

          <div className={styles.linkSection}>
            <label htmlFor="buyer-confirm-url">구매자 최종 승인 링크</label>
            {request.buyerConfirmUrl ? (
              <div className={styles.linkControls}>
                <input
                  id="buyer-confirm-url"
                  readOnly
                  value={request.buyerConfirmUrl}
                  onFocus={(event) => event.currentTarget.select()}
                />
                <button
                  aria-label="구매자 최종 승인 링크 복사"
                  type="button"
                  onClick={() => void copyUrl(request.buyerConfirmUrl!, 'buyer')}
                >
                  {buyerCopyState === 'copied' ? '복사됨' : '링크 복사'}
                </button>
              </div>
            ) : (
              <p className={styles.missingValue}>
                {canEditFinalAmount || request.status === 'SELLER_INPUT_PENDING'
                  ? '최종 금액을 저장하면 구매자 승인 링크가 표시됩니다.'
                  : '발급된 구매자 승인 링크가 없습니다.'}
              </p>
            )}
            {buyerCopyState === 'copied' && (
              <p className={styles.copySuccess} role="status">
                구매자 최종 승인 링크를 복사했습니다.
              </p>
            )}
            {buyerCopyState === 'error' && (
              <p className={styles.copyError} role="alert">
                링크를 복사하지 못했습니다. 링크를 직접 선택해 복사해 주세요.
              </p>
            )}
          </div>

          <div className={styles.recordSection}>
            <h3>메시지 기록</h3>
            <p className={styles.operationNotice}>
              이 화면은 문자를 보내지 않습니다. 구매자에게 직접 보낸 문자와 이후 운영
              상황을 한 칸 기록으로 계속 수정해 주세요.
            </p>
            <form
              className={styles.operationForm}
              noValidate
              onSubmit={handleMessageSubmit}
            >
              <div className={styles.fieldGroup}>
                <label htmlFor="dispatch-message-content">문자 안내 및 상황 기록</label>
                <textarea
                  aria-describedby={
                    messageError ? 'dispatch-message-content-error' : undefined
                  }
                  aria-invalid={Boolean(messageError)}
                  disabled={isMutating}
                  id="dispatch-message-content"
                  name="messageContent"
                  required
                  rows={7}
                  value={messageContent}
                  onChange={(event) => {
                    setMessageContent(event.target.value);
                    setMessageError('');
                    setMessageSaveError('');
                    setMessageSaveSuccess('');
                  }}
                />
                {messageError && (
                  <small id="dispatch-message-content-error" role="alert">
                    {messageError}
                  </small>
                )}
              </div>

              {messageSaveError && (
                <div className={styles.submitError} role="alert">
                  {messageSaveError}
                </div>
              )}
              {messageSaveSuccess && (
                <p className={styles.submitSuccess} role="status">
                  {messageSaveSuccess}
                </p>
              )}

              <button disabled={isMutating} type="submit">
                {isSavingMessage
                  ? '저장하고 있어요…'
                  : request.messageContent
                    ? '메시지 수정'
                    : '메시지 저장'}
              </button>
            </form>
          </div>

          {request.status === 'DISPATCH_PENDING' && (
            <div className={styles.completionSection}>
              <strong>실제 배차를 마쳤나요?</strong>
              <p>외부 운송 채널에서 기사 배정까지 마친 뒤 상태를 변경해 주세요.</p>
              {completionError && (
                <div className={styles.submitError} role="alert">
                  {completionError}
                </div>
              )}
              <button
                disabled={isMutating}
                type="button"
                onClick={() => void handleCompleteDispatch()}
              >
                {isCompleting ? '변경하고 있어요…' : '배차 완료로 변경'}
              </button>
            </div>
          )}

          {completionSuccess && (
            <p className={styles.submitSuccess} role="status">
              {completionSuccess}
            </p>
          )}
        </article>
      </div>
    </section>
  );
}
