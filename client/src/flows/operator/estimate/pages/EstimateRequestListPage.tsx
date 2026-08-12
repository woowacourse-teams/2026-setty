import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '@/shared/api/http';
import {
  getOperatorEstimateRequests,
  OperatorEstimateRequestSummary,
} from '@/flows/operator/estimate/api/operatorEstimateApi';
import {
  formatKoreanDateTime,
  getEstimateStatusLabel,
} from '@/flows/operator/estimate/presentation';
import styles from './OperatorEstimatePages.module.css';

type ListState = 'loading' | 'ready' | 'error';

export default function EstimateRequestListPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [requests, setRequests] = useState<OperatorEstimateRequestSummary[]>([]);
  const [listState, setListState] = useState<ListState>('loading');
  const [retryKey, setRetryKey] = useState(0);

  const moveToLogin = useCallback(() => {
    navigate('/operator/login', {
      replace: true,
      state: { from: `${location.pathname}${location.search}` },
    });
  }, [location.pathname, location.search, navigate]);

  useEffect(() => {
    const controller = new AbortController();
    void getOperatorEstimateRequests(controller.signal)
      .then((response) => {
        setRequests(response);
        setListState('ready');
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.status === 401) {
          moveToLogin();
          return;
        }
        setListState('error');
      });

    return () => controller.abort();
  }, [moveToLogin, retryKey]);

  return (
    <section aria-labelledby="operator-estimate-list-title">
      <header className={styles.pageHeader}>
        <div>
          <p>예상 견적 운영</p>
          <h1 id="operator-estimate-list-title">견적 요청 목록</h1>
        </div>
        {listState === 'ready' && <span>총 {requests.length}건</span>}
      </header>

      {listState === 'loading' && (
        <div className={styles.stateCard} role="status">
          요청 목록을 불러오고 있어요…
        </div>
      )}

      {listState === 'error' && (
        <div className={styles.stateCard}>
          <h2>목록을 불러오지 못했어요</h2>
          <p>서버 연결을 확인한 뒤 다시 시도해 주세요.</p>
          <button
            type="button"
            onClick={() => {
              setListState('loading');
              setRetryKey((current) => current + 1);
            }}
          >
            다시 시도
          </button>
        </div>
      )}

      {listState === 'ready' && requests.length === 0 && (
        <div className={styles.stateCard}>
          <h2>접수된 견적 요청이 없어요</h2>
          <p>새 요청이 접수되면 이곳에서 확인할 수 있습니다.</p>
        </div>
      )}

      {listState === 'ready' && requests.length > 0 && (
        <div className={styles.tableCard}>
          <div className={styles.tableScroll}>
            <table>
              <thead>
                <tr>
                  <th scope="col">요청 ID</th>
                  <th scope="col">거래 지역</th>
                  <th scope="col">물품 종류</th>
                  <th scope="col">50만 원 초과</th>
                  <th scope="col">상태</th>
                  <th scope="col">접수 시각</th>
                </tr>
              </thead>
              <tbody>
                {requests.map((request) => (
                  <tr
                    key={request.estimateRequestId}
                    onClick={(event) => {
                      if (
                        event.defaultPrevented ||
                        (event.target instanceof Element &&
                          event.target.closest('a, button, input, select, textarea'))
                      ) {
                        return;
                      }

                      navigate(
                        `/operator/estimate-requests/${request.estimateRequestId}`,
                      );
                    }}
                  >
                    <td>
                      <Link
                        to={`/operator/estimate-requests/${request.estimateRequestId}`}
                      >
                        #{request.estimateRequestId}
                      </Link>
                    </td>
                    <td>{request.tradeArea}</td>
                    <td>{request.itemType}</td>
                    <td>{request.highValueItem ? '예' : '아니요'}</td>
                    <td>
                      <span className={styles.status} data-status={request.status}>
                        {getEstimateStatusLabel(request.status)}
                      </span>
                    </td>
                    <td>{formatKoreanDateTime(request.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
