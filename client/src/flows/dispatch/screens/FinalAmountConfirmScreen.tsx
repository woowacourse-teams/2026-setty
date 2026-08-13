import { useCallback, useEffect, useState } from 'react';
import { approveFinalAmount, findBuyerDispatchRequest } from '../api/dispatchApi';
import { DispatchApiError } from '../api/dispatchClient';
import checkIcon from '../assets/result-check.png';
import hourglassIcon from '../assets/result-hourglass.png';
import truckIcon from '../assets/result-truck.png';
import BrandHeader from '../components/BrandHeader';
import ConfirmBottomSheet from '../components/ConfirmBottomSheet';
import MobileScreen from '../components/MobileScreen';
import PrimaryButton from '../components/PrimaryButton';
import ResultMessage from '../components/ResultMessage';
import { ErrorMessage, LoadingMessage } from '../components/StatusMessage';
import TextButton from '../components/TextButton';
import { toDispatchStatusLabel } from '../model/dispatchStatusLabel';
import type { BuyerDispatchRequestResponse } from '../model/dispatchTypes';
import styles from './FinalAmountConfirmScreen.module.css';

interface FinalAmountConfirmScreenProps {
  /** POST /api/dispatch-requests 응답의 buyerToken */
  buyerToken: string;
}

const DEFAULT_ERROR_MESSAGE = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';

/**
 * 시안 카피의 금액은 예시이며 화면에 하드코딩하지 않는다.
 * 자동 가격 계산은 `mvp-scope.md` 4장에서 제외됐고, 여기 금액은 운영자가
 * 외부 채널에서 조회해 기록한 `finalQuotedAmount`를 그대로 보여주는 것이다.
 */
const CONFIRM_DESCRIPTION = '진행하면 안전하게 배송이 시작돼요.';

/** 동의는 되돌릴 수 없으므로 요청을 보내기 전에 시안의 바텀 시트로 한 번 더 묻는다. */
const CONFIRM_SHEET = {
  title: '이대로 진행할까요?',
  confirmLabel: '네, 진행할게요',
  cancelLabel: '다시 볼게요',
};

/** 아직 운영자가 최종 금액을 기록하지 않은 상태에서 링크로 들어온 경우다. */
const WAITING_MESSAGE = {
  iconSrc: hourglassIcon,
  title: '최종 금액을 확인하고 있어요',
  description: '운영자가 확인한 최종 금액과 조건을\n문자(SMS)로 안내해 드릴게요.',
};

/** 동의 이후에는 되돌릴 action이 없으므로 현재 상태만 알린다. */
const APPROVED_MESSAGE = {
  iconSrc: checkIcon,
  title: '진행하기로 확인했어요',
  description: '운영자가 배차를 준비하고\n진행 상황을 문자(SMS)로 알려드릴게요.',
};

/** 이미 끝난 요청에는 동의·취소할 대상이 없다. */
const CLOSED_MESSAGE = {
  emoji: '🛑',
  title: '진행이 종료됐어요',
  description: '자세한 사유는 운영자가\n문자(SMS)로 안내해 드릴게요.',
};

const CLOSED_STATUSES: readonly BuyerDispatchRequestResponse['status'][] = [
  'FINAL_AMOUNT_REJECTED',
  'USER_CANCELLED',
  'TRANSPORT_INFEASIBLE',
];

const formatAmount = (amount: number): string => `${amount.toLocaleString('ko-KR')}원`;

const toErrorMessage = (error: unknown): string =>
  error instanceof DispatchApiError ? error.message : DEFAULT_ERROR_MESSAGE;

/**
 * 운영자가 안내한 최종 금액을 구매자가 확인하고 진행하는 화면이다.
 * 판매자 정보와 운영 메모는 `DEC-017`에 따라 이 화면에서 보여주지 않는다.
 */
export default function FinalAmountConfirmScreen({
  buyerToken,
}: FinalAmountConfirmScreenProps) {
  const [request, setRequest] = useState<BuyerDispatchRequestResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  /** "다시 시도"와 동의 성공 후 조회를 다시 실행하기 위한 값이다. */
  const [reloadCount, setReloadCount] = useState(0);
  /** 같은 동의를 두 번 보내지 않도록 전송 중에는 버튼을 잠근다. */
  const [submitting, setSubmitting] = useState(false);
  const [actionErrorMessage, setActionErrorMessage] = useState('');
  /** 동의 요청은 확인 바텀 시트를 거친 뒤에만 나간다. */
  const [confirmSheetOpen, setConfirmSheetOpen] = useState(false);

  useEffect(() => {
    // StrictMode 이중 마운트와 언마운트 후 setState를 막는다.
    let active = true;

    findBuyerDispatchRequest(buyerToken)
      .then((response) => {
        if (!active) {
          return;
        }
        setRequest(response);
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }
        setRequest(null);
        setErrorMessage(toErrorMessage(error));
      })
      .finally(() => {
        if (!active) {
          return;
        }
        setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [buyerToken, reloadCount]);

  /** 조회 상태 초기화는 effect 본문이 아니라 재시도 event에서 한다. */
  const handleRetry = useCallback(() => {
    setLoading(true);
    setErrorMessage('');
    setReloadCount((count) => count + 1);
  }, []);

  /**
   * 동의 결과를 client에서 추측해 그리지 않고 server 상태를 다시 읽는다.
   * 다른 창에서 이미 처리했거나 운영자가 금액을 고친 경우도 같은 경로로 드러난다.
   */
  const handleApprove = useCallback(() => {
    setSubmitting(true);
    setActionErrorMessage('');

    approveFinalAmount(buyerToken)
      .then(() => {
        setConfirmSheetOpen(false);
        setReloadCount((count) => count + 1);
      })
      .catch((error: unknown) => {
        // 실패는 시트를 연 채로 알리고 그 자리에서 다시 시도하게 한다.
        setActionErrorMessage(toErrorMessage(error));
      })
      .finally(() => {
        setSubmitting(false);
      });
  }, [buyerToken]);

  /** 시트를 닫으면 요청을 보내지 않고 확인 화면을 그대로 둔다. */
  const handleCloseConfirmSheet = useCallback(() => {
    setConfirmSheetOpen(false);
    setActionErrorMessage('');
  }, []);

  const handleOpenConfirmSheet = useCallback(() => {
    setActionErrorMessage('');
    setConfirmSheetOpen(true);
  }, []);

  const loaded = !loading && !errorMessage && request !== null;
  /** 금액이 없으면 확인할 대상이 없으므로 상태와 무관하게 대기 안내를 보여준다. */
  const finalAmount = request?.finalQuotedAmount ?? null;
  const confirmable =
    loaded && request?.status === 'FINAL_AMOUNT_CONFIRM_PENDING' && finalAmount !== null;

  return (
    <MobileScreen
      header={<BrandHeader />}
      footer={
        confirmable ? (
          <>
            <PrimaryButton onClick={handleOpenConfirmSheet} disabled={submitting}>
              진행하기
            </PrimaryButton>
            {/* 시안의 두 번째 action이지만 연결할 server 엔드포인트가 없어 비활성이다. */}
            <TextButton disabled>거래 취소</TextButton>
          </>
        ) : null
      }
    >
      {loading ? <LoadingMessage /> : null}

      {!loading && errorMessage ? (
        <div className={styles.errorArea}>
          <ErrorMessage message={errorMessage} />
          <TextButton onClick={handleRetry}>다시 시도</TextButton>
        </div>
      ) : null}

      {loaded && request ? (
        <FinalAmountBody status={request.status} finalAmount={finalAmount} />
      ) : null}

      {confirmable && finalAmount !== null ? (
        <ConfirmBottomSheet
          open={confirmSheetOpen}
          title={CONFIRM_SHEET.title}
          description={
            <>
              {'진행하면 '}
              {/* 화면에 보여준 값과 같은 server 응답을 그대로 쓴다. */}
              <span className={styles.sheetAmount}>{formatAmount(finalAmount)}</span>
              {'으로 배송이 시작되고\n이후에는 취소할 수 없어요.'}
            </>
          }
          confirmLabel={CONFIRM_SHEET.confirmLabel}
          cancelLabel={CONFIRM_SHEET.cancelLabel}
          submitting={submitting}
          errorMessage={actionErrorMessage}
          onConfirm={handleApprove}
          onClose={handleCloseConfirmSheet}
        />
      ) : null}
    </MobileScreen>
  );
}

interface FinalAmountBodyProps {
  status: BuyerDispatchRequestResponse['status'];
  finalAmount: number | null;
}

function FinalAmountBody({ status, finalAmount }: FinalAmountBodyProps) {
  if (status === 'FINAL_AMOUNT_CONFIRM_PENDING' && finalAmount !== null) {
    return (
      <ResultMessage
        iconSrc={truckIcon}
        title={
          <>
            {/* 시안처럼 금액만 강조하고, 값 자체는 server 응답을 그대로 쓴다. */}
            <span className={styles.amount}>{formatAmount(finalAmount)}</span>
            이면 배달돼요
          </>
        }
        description={CONFIRM_DESCRIPTION}
      />
    );
  }

  if (CLOSED_STATUSES.includes(status)) {
    return (
      <ResultMessage
        emoji={CLOSED_MESSAGE.emoji}
        title={CLOSED_MESSAGE.title}
        description={CLOSED_MESSAGE.description}
      >
        {/* 내부 enum 대신 사용자 표시 문구만 노출한다. */}
        <p className={styles.status}>{toDispatchStatusLabel(status)}</p>
      </ResultMessage>
    );
  }

  if (finalAmount === null) {
    return (
      <ResultMessage
        iconSrc={WAITING_MESSAGE.iconSrc}
        title={WAITING_MESSAGE.title}
        description={WAITING_MESSAGE.description}
      />
    );
  }

  /* 동의 이후의 진행·완료·실패는 상태 문구로만 알린다. */
  return (
    <ResultMessage
      iconSrc={APPROVED_MESSAGE.iconSrc}
      title={APPROVED_MESSAGE.title}
      description={APPROVED_MESSAGE.description}
    >
      <p className={styles.status}>{toDispatchStatusLabel(status)}</p>
    </ResultMessage>
  );
}
