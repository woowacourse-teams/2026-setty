import { useCallback, useEffect, useState } from 'react';
import { findBuyerDispatchRequest } from '../api/dispatchApi';
import { DispatchApiError } from '../api/dispatchClient';
import BrandHeader from '../components/BrandHeader';
import MobileScreen from '../components/MobileScreen';
import PrimaryButton from '../components/PrimaryButton';
import ResultMessage from '../components/ResultMessage';
import { ErrorMessage, LoadingMessage } from '../components/StatusMessage';
import TextButton from '../components/TextButton';
import type { BuyerDispatchRequestResponse } from '../model/dispatchTypes';
import styles from './SellerWaitingScreen.module.css';

interface SellerWaitingScreenProps {
  /** POST /api/dispatch-requests 응답의 buyerToken */
  buyerToken: string;
  onGoHome: () => void;
}

const DEFAULT_ERROR_MESSAGE = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';

/**
 * 판매자는 별도 링크로 입력하므로 구매자 화면에 완료를 알릴 수단이 없다.
 * 실시간 채널 계약이 없는 MVP에서는 조회를 주기적으로 반복해 완료를 감지한다.
 */
const POLL_INTERVAL_MS = 5000;

/** 시안 카피. 문자는 운영자가 직접 보내는 수동 절차라 자동 발송으로 표현하지 않는다. */
const WAITING_MESSAGE = {
  emoji: '⌛',
  title: '판매자를 기다리고 있어요',
  description: '판매자가 사진·주소를 입력하면\n문자(SMS)로 알려드릴게요.',
};

/**
 * 판매자 입력이 끝난 뒤에도 구매자에게 판매자 정보를 보여주지 않는다.
 * `DEC-017`에 따라 완료 여부만 알린다.
 */
const COMPLETED_MESSAGE = {
  emoji: '✅',
  title: '판매자 입력이 완료됐어요',
  description: '운영자가 배송 조건을 확인한 뒤\n최종 금액과 조건을 문자로 직접 안내해요.',
};

/**
 * 구매자가 링크를 만든 뒤 판매자 입력을 기다리는 화면이다.
 * 응답의 구매자 본인 정보와 판매자 정보는 이 화면에서 렌더링하지 않는다.
 */
export default function SellerWaitingScreen({
  buyerToken,
  onGoHome,
}: SellerWaitingScreenProps) {
  const [request, setRequest] = useState<BuyerDispatchRequestResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  /** "다시 시도"로 조회를 다시 실행하기 위한 값이다. */
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    // StrictMode 이중 마운트와 언마운트 후 setState를 막는다.
    let active = true;
    let timerId: ReturnType<typeof setTimeout> | undefined;

    const poll = () => {
      findBuyerDispatchRequest(buyerToken)
        .then((response) => {
          if (!active) {
            return;
          }
          setRequest(response);
          // 판매자 입력이 끝나면 더 조회할 이유가 없다.
          if (!response.sellerInputCompleted) {
            timerId = setTimeout(poll, POLL_INTERVAL_MS);
          }
        })
        .catch((error: unknown) => {
          if (!active) {
            return;
          }
          setRequest(null);
          setErrorMessage(
            error instanceof DispatchApiError ? error.message : DEFAULT_ERROR_MESSAGE,
          );
          // 실패는 자동 반복하지 않고 "다시 시도"에 맡긴다.
        })
        .finally(() => {
          if (!active) {
            return;
          }
          setLoading(false);
        });
    };

    poll();

    return () => {
      active = false;
      clearTimeout(timerId);
    };
  }, [buyerToken, retryCount]);

  /** 조회 상태 초기화는 effect 본문이 아니라 재시도 event에서 한다. */
  const handleRetry = useCallback(() => {
    setLoading(true);
    setErrorMessage('');
    setRetryCount((count) => count + 1);
  }, []);

  const message = request?.sellerInputCompleted ? COMPLETED_MESSAGE : WAITING_MESSAGE;

  return (
    <MobileScreen
      header={<BrandHeader />}
      footer={<PrimaryButton onClick={onGoHome}>홈으로 돌아가기</PrimaryButton>}
    >
      {loading ? <LoadingMessage /> : null}

      {!loading && errorMessage ? (
        <div className={styles.errorArea}>
          <ErrorMessage message={errorMessage} />
          <TextButton onClick={handleRetry}>다시 시도</TextButton>
        </div>
      ) : null}

      {!loading && !errorMessage && request ? (
        <ResultMessage
          emoji={message.emoji}
          title={message.title}
          description={message.description}
        />
      ) : null}
    </MobileScreen>
  );
}
