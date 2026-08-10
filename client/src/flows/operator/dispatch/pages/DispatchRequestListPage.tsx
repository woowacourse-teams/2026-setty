import { ChangeEvent, MouseEvent, useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '@/shared/api/http';
import {
  DISPATCH_STATUSES,
  DispatchStatus,
  getOperatorDispatchRequests,
  isDispatchStatus,
  OperatorDispatchRequestSummary,
} from '@/flows/operator/dispatch/api/operatorDispatchApi';
import {
  formatAmount,
  formatKoreanDateTime,
  getDispatchStatusLabel,
} from '@/flows/operator/dispatch/presentation';
import styles from './OperatorDispatchPages.module.css';

type ListState = 'loading' | 'ready' | 'error';

export default function DispatchRequestListPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const statusParam = searchParams.get('status');
  const selectedStatus = isDispatchStatus(statusParam) ? statusParam : undefined;
  const [requests, setRequests] = useState<OperatorDispatchRequestSummary[]>([]);
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

    void getOperatorDispatchRequests(selectedStatus, controller.signal)
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
  }, [moveToLogin, retryKey, selectedStatus]);

  const handleStatusChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const nextStatus = event.target.value as DispatchStatus | '';
    const nextSearchParams = new URLSearchParams(searchParams);
    setListState('loading');

    if (nextStatus) {
      nextSearchParams.set('status', nextStatus);
    } else {
      nextSearchParams.delete('status');
    }

    setSearchParams(nextSearchParams, { replace: true });
  };

  const handleRowClick = (
    event: MouseEvent<HTMLTableRowElement>,
    dispatchRequestId: number,
  ) => {
    if (
      event.defaultPrevented ||
      (event.target instanceof Element &&
        event.target.closest('a, button, input, select, textarea'))
    ) {
      return;
    }

    navigate(`/operator/dispatch-requests/${dispatchRequestId}`);
  };

  return (
    <section aria-labelledby="operator-dispatch-list-title">
      <header className={styles.pageHeader}>
        <div>
          <p>배차 운영</p>
          <h1 id="operator-dispatch-list-title">배차 요청 목록</h1>
        </div>
        {listState === 'ready' && <span>총 {requests.length}건</span>}
      </header>

      <div className={styles.filterBar}>
        <label htmlFor="dispatch-status-filter">상태</label>
        <select
          id="dispatch-status-filter"
          value={selectedStatus ?? ''}
          onChange={handleStatusChange}
        >
          <option value="">전체</option>
          {DISPATCH_STATUSES.map((status) => (
            <option key={status} value={status}>
              {getDispatchStatusLabel(status)}
            </option>
          ))}
        </select>
      </div>

      {listState === 'loading' && (
        <div className={styles.stateCard} role="status">
          배차 요청 목록을 불러오고 있어요…
        </div>
      )}

      {listState === 'error' && (
        <div className={styles.stateCard} role="alert">
          <h2>배차 요청 목록을 불러오지 못했어요</h2>
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
          <h2>조건에 맞는 배차 요청이 없어요</h2>
          <p>
            {selectedStatus
              ? '다른 상태를 선택하거나 새 요청이 접수된 뒤 확인해 주세요.'
              : '새 요청이 접수되면 이곳에서 확인할 수 있습니다.'}
          </p>
        </div>
      )}

      {listState === 'ready' && requests.length > 0 && (
        <div className={styles.tableCard}>
          <div className={styles.tableScroll}>
            <table>
              <thead>
                <tr>
                  <th scope="col">요청 ID</th>
                  <th scope="col">물품 종류</th>
                  <th scope="col">50만 원 초과</th>
                  <th scope="col">판매자 입력</th>
                  <th scope="col">최종 금액</th>
                  <th scope="col">상태</th>
                  <th scope="col">접수 시각</th>
                </tr>
              </thead>
              <tbody>
                {requests.map((request) => (
                  <tr
                    key={request.id}
                    onClick={(event) => handleRowClick(event, request.id)}
                  >
                    <td>
                      <Link to={`/operator/dispatch-requests/${request.id}`}>
                        #{request.id}
                      </Link>
                    </td>
                    <td>{request.itemType}</td>
                    <td>{request.highValueItem ? '예' : '아니요'}</td>
                    <td>{request.sellerInputCompleted ? '입력 완료' : '입력 대기'}</td>
                    <td>{formatAmount(request.finalQuotedAmount)}</td>
                    <td>
                      <span className={styles.status} data-status={request.status}>
                        {getDispatchStatusLabel(request.status)}
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
