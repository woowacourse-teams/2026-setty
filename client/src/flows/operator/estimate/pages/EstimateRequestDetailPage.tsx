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
  getOperatorEstimateRequest,
  OperatorEstimateRequestDetail,
  saveManualNotification,
} from '@/flows/operator/estimate/api/operatorEstimateApi';
import {
  formatKoreanDateTime,
  formatOperatorPhoneNumber,
  getEstimateStatusLabel,
} from '@/flows/operator/estimate/presentation';
import styles from './OperatorEstimatePages.module.css';

type DetailState = 'loading' | 'ready' | 'not-found' | 'error';
type FeasibilityValue = '' | 'true' | 'false';

interface NotificationErrors {
  messageContent?: string;
  transportFeasible?: string;
}

export default function EstimateRequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [request, setRequest] = useState<OperatorEstimateRequestDetail | null>(null);
  const [detailState, setDetailState] = useState<DetailState>('loading');
  const [retryKey, setRetryKey] = useState(0);
  const [messageContent, setMessageContent] = useState('');
  const [transportFeasible, setTransportFeasible] = useState<FeasibilityValue>('');
  const [notificationErrors, setNotificationErrors] = useState<NotificationErrors>({});
  const [submitError, setSubmitError] = useState('');
  const [submitSuccess, setSubmitSuccess] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const activeRouteIdRef = useRef(id);

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

    void getOperatorEstimateRequest(id, controller.signal)
      .then((response) => {
        if (activeRouteIdRef.current !== id) return;

        setRequest(response);
        setMessageContent(response.manualNotification?.messageContent ?? '');
        setTransportFeasible(
          response.manualNotification
            ? response.manualNotification.transportFeasible
              ? 'true'
              : 'false'
            : '',
        );
        setNotificationErrors({});
        setSubmitError('');
        setSubmitSuccess('');
        setIsSubmitting(false);
        setDetailState('ready');
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || activeRouteIdRef.current !== id) return;
        if (error instanceof ApiError && error.status === 401) {
          moveToLogin();
          return;
        }
        setDetailState(
          error instanceof ApiError && error.status === 404 ? 'not-found' : 'error',
        );
      });

    return () => controller.abort();
  }, [id, moveToLogin, retryKey]);

  const validateNotification = (): NotificationErrors => {
    const errors: NotificationErrors = {};
    if (!messageContent.trim()) {
      errors.messageContent = '실제로 보낸 문자 내용을 입력해 주세요.';
    }
    if (!transportFeasible) {
      errors.transportFeasible = '운송 가능 여부를 선택해 주세요.';
    }
    return errors;
  };

  const handleNotificationSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!id || !request || request.estimateRequestId !== Number(id)) return;

    const errors = validateNotification();
    if (Object.keys(errors).length > 0) {
      setNotificationErrors(errors);
      return;
    }

    setIsSubmitting(true);
    setNotificationErrors({});
    setSubmitError('');
    setSubmitSuccess('');

    try {
      const savedMessageContent = messageContent.trim();
      const savedTransportFeasible = transportFeasible === 'true';
      await saveManualNotification(id, {
        messageContent: savedMessageContent,
        transportFeasible: savedTransportFeasible,
      });
      if (activeRouteIdRef.current !== id) return;

      setRequest((current) =>
        current?.estimateRequestId === Number(id)
          ? {
              ...current,
              status: 'ESTIMATE_NOTIFIED',
              manualNotification: {
                messageContent: savedMessageContent,
                transportFeasible: savedTransportFeasible,
              },
            }
          : current,
      );
      setMessageContent(savedMessageContent);
      setSubmitSuccess('메시지를 저장했습니다.');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        moveToLogin();
      } else if (activeRouteIdRef.current !== id) {
        return;
      } else if (error instanceof ApiError && error.status === 409) {
        setSubmitError(
          '요청 상태가 변경되어 메시지를 저장하지 못했습니다. 입력 내용을 유지했으니 필요하면 복사한 뒤 페이지를 새로고침해 주세요.',
        );
      } else if (error instanceof ApiError && error.status === 400 && error.fieldErrors) {
        setNotificationErrors({
          messageContent: error.fieldErrors.messageContent,
          transportFeasible: error.fieldErrors.transportFeasible,
        });
      } else {
        setSubmitError('메시지를 저장하지 못했습니다. 다시 시도해 주세요.');
      }
    } finally {
      if (activeRouteIdRef.current === id) {
        setIsSubmitting(false);
      }
    }
  };

  if (
    detailState === 'loading' ||
    (detailState === 'ready' &&
      id !== undefined &&
      request !== null &&
      request.estimateRequestId !== Number(id))
  ) {
    return (
      <div className={styles.stateCard} role="status">
        견적 요청을 불러오고 있어요…
      </div>
    );
  }

  if (!id || detailState === 'not-found') {
    return (
      <div className={styles.stateCard}>
        <h1>견적 요청을 찾을 수 없어요</h1>
        <Link to="/operator/estimate-requests">목록으로 돌아가기</Link>
      </div>
    );
  }

  if (detailState === 'error' || !request) {
    return (
      <div className={styles.stateCard}>
        <h1>견적 요청을 불러오지 못했어요</h1>
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

  return (
    <section aria-labelledby="operator-estimate-detail-title">
      <Link className={styles.backLink} to="/operator/estimate-requests">
        ← 견적 목록
      </Link>

      <header className={styles.detailHeader}>
        <div>
          <p>견적 요청 #{request.estimateRequestId}</p>
          <h1 id="operator-estimate-detail-title">견적 요청 상세</h1>
        </div>
        <span className={styles.status} data-status={request.status}>
          {getEstimateStatusLabel(request.status)}
        </span>
      </header>

      <div className={styles.detailGrid}>
        <article className={styles.infoCard}>
          <h2>사용자 입력 정보</h2>
          <dl>
            <div>
              <dt>이름</dt>
              <dd>{request.name}</dd>
            </div>
            <div>
              <dt>연락처</dt>
              <dd>{formatOperatorPhoneNumber(request.phoneNumber)}</dd>
            </div>
            <div>
              <dt>거래 지역</dt>
              <dd>{request.tradeArea}</dd>
            </div>
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
          </dl>
        </article>

        {request.status === 'PENDING_REVIEW' || request.manualNotification !== null ? (
          <article className={styles.notificationCard}>
            <h2>문자 안내 기록</h2>
            <p className={styles.manualNotice}>
              이 화면은 문자를 보내지 않습니다. 실제로 보낸 문자와 이후 상황을 기존 내용에
              이어 기록해 주세요.
            </p>

            <form
              className={styles.notificationForm}
              noValidate
              onSubmit={handleNotificationSubmit}
            >
              <fieldset
                aria-describedby={
                  notificationErrors.transportFeasible
                    ? 'transport-feasible-error'
                    : undefined
                }
                aria-required="true"
              >
                <legend>운송 가능 여부</legend>
                <div className={styles.inlineChoices}>
                  <label>
                    <input
                      checked={transportFeasible === 'true'}
                      name="transportFeasible"
                      required
                      type="radio"
                      value="true"
                      onChange={(event) => {
                        setTransportFeasible(event.target.value as FeasibilityValue);
                        setSubmitError('');
                        setSubmitSuccess('');
                        setNotificationErrors((current) => ({
                          ...current,
                          transportFeasible: undefined,
                        }));
                      }}
                    />
                    운송 가능
                  </label>
                  <label>
                    <input
                      checked={transportFeasible === 'false'}
                      name="transportFeasible"
                      required
                      type="radio"
                      value="false"
                      onChange={(event) => {
                        setTransportFeasible(event.target.value as FeasibilityValue);
                        setSubmitError('');
                        setSubmitSuccess('');
                        setNotificationErrors((current) => ({
                          ...current,
                          transportFeasible: undefined,
                        }));
                      }}
                    />
                    운송 불가
                  </label>
                </div>
                {notificationErrors.transportFeasible && (
                  <small id="transport-feasible-error" role="alert">
                    {notificationErrors.transportFeasible}
                  </small>
                )}
              </fieldset>

              <label htmlFor="messageContent">
                <span>문자 안내 및 상황 기록</span>
                <textarea
                  aria-describedby={
                    notificationErrors.messageContent
                      ? 'message-content-error'
                      : undefined
                  }
                  aria-invalid={Boolean(notificationErrors.messageContent)}
                  id="messageContent"
                  name="messageContent"
                  required
                  rows={7}
                  value={messageContent}
                  onChange={(event) => {
                    setMessageContent(event.target.value);
                    setSubmitError('');
                    setSubmitSuccess('');
                    setNotificationErrors((current) => ({
                      ...current,
                      messageContent: undefined,
                    }));
                  }}
                />
                {notificationErrors.messageContent && (
                  <small id="message-content-error" role="alert">
                    {notificationErrors.messageContent}
                  </small>
                )}
              </label>

              {submitError && (
                <div className={styles.submitError} role="alert">
                  {submitError}
                </div>
              )}

              {submitSuccess && (
                <p className={styles.submitSuccess} role="status">
                  {submitSuccess}
                </p>
              )}

              <button disabled={isSubmitting} type="submit">
                {isSubmitting ? '저장하고 있어요…' : '메시지 저장'}
              </button>
            </form>
          </article>
        ) : (
          <article className={styles.notificationCard}>
            <h2>문자 안내 기록</h2>
            <p className={styles.contractWarning} role="alert">
              안내 완료 상태이지만 저장된 문자 기록을 받지 못했습니다. 서버 응답 계약을
              확인해 주세요.
            </p>
          </article>
        )}
      </div>
    </section>
  );
}
