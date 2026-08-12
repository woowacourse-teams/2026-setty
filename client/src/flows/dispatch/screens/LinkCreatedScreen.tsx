import { useCallback, useEffect, useState } from 'react';
import { findBuyerDispatchRequest } from '../api/dispatchApi';
import { DispatchApiError } from '../api/dispatchClient';
import BrandHeader from '../components/BrandHeader';
import MobileScreen from '../components/MobileScreen';
import PrimaryButton from '../components/PrimaryButton';
import ResultMessage from '../components/ResultMessage';
import SecondaryButton from '../components/SecondaryButton';
import { ErrorMessage, LoadingMessage } from '../components/StatusMessage';
import TextButton from '../components/TextButton';
import { buildSellerGuideMessage } from '../model/sellerGuideMessage';
import styles from './LinkCreatedScreen.module.css';

interface LinkCreatedScreenProps {
  /** POST /api/dispatch-requests 응답의 buyerToken */
  buyerToken: string;
  /**
   * 생성 직후 응답으로 이미 알고 있는 sellerInputUrl이다.
   * 새로고침·뒤로가기로 이 값이 사라지면 buyerToken으로 다시 조회한다.
   */
  initialSellerInputUrl?: string;
  /** 공유·복사를 마치거나 직접 선택해 다음 화면(판매자 대기)으로 이동 */
  onNext: () => void;
}

const DEFAULT_ERROR_MESSAGE = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';

/** 공유 시트를 사용자가 닫은 경우로, 실패로 안내하지 않는다. */
function isAbortError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'name' in error &&
    (error as { name?: unknown }).name === 'AbortError'
  );
}

function canUseShare(): boolean {
  return typeof navigator !== 'undefined' && typeof navigator.share === 'function';
}

/** jsdom과 비보안 컨텍스트에는 clipboard가 없다. */
function canUseClipboard(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    typeof navigator.clipboard?.writeText === 'function'
  );
}

/**
 * 배차 요청 생성 직후 판매자 안내문과 입력 링크를 함께 전달하는 화면이다.
 * 서버를 호출하지 않고 브라우저 공유·복사만 사용한다.
 */
export default function LinkCreatedScreen({
  buyerToken,
  initialSellerInputUrl,
  onNext,
}: LinkCreatedScreenProps) {
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [sellerInputUrl, setSellerInputUrl] = useState(initialSellerInputUrl ?? '');
  const [loading, setLoading] = useState(!initialSellerInputUrl);
  const [loadError, setLoadError] = useState('');
  /** "다시 시도"로 조회를 다시 실행하기 위한 값이다. */
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    // 생성 직후에는 링크를 이미 알고 있어 같은 값을 다시 받아올 이유가 없다.
    if (initialSellerInputUrl) {
      return;
    }

    // StrictMode 이중 마운트와 언마운트 후 setState를 막는다.
    let active = true;

    findBuyerDispatchRequest(buyerToken)
      .then((response) => {
        if (!active) {
          return;
        }
        setSellerInputUrl(response.sellerInputUrl);
      })
      .catch((requestError: unknown) => {
        if (!active) {
          return;
        }
        setSellerInputUrl('');
        setLoadError(
          requestError instanceof DispatchApiError
            ? requestError.message
            : DEFAULT_ERROR_MESSAGE,
        );
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
  }, [buyerToken, initialSellerInputUrl, retryCount]);

  /** 조회 상태 초기화는 effect 본문이 아니라 재시도 event에서 한다. */
  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError('');
    setRetryCount((count) => count + 1);
  }, []);

  const shareSupported = canUseShare();
  const clipboardSupported = canUseClipboard();
  // 두 API 모두 못 쓰거나 실패했을 때만 안내문 전문을 그대로 보여준다.
  const showRawMessage = (!shareSupported && !clipboardSupported) || error !== null;
  const guideMessage = buildSellerGuideMessage(sellerInputUrl);

  async function copyGuideMessage(): Promise<void> {
    if (!clipboardSupported) {
      setError(
        '이 브라우저에서는 안내문을 자동으로 복사할 수 없어요. 아래 안내문을 직접 복사해 주세요.',
      );
      return;
    }

    try {
      await navigator.clipboard.writeText(guideMessage);
    } catch {
      setError('안내문을 복사하지 못했어요. 아래 안내문을 직접 복사해 주세요.');
      return;
    }

    setNotice('안내문과 링크를 복사했어요');
    onNext();
  }

  async function handleShare(): Promise<void> {
    setNotice(null);
    setError(null);

    if (!shareSupported) {
      await copyGuideMessage();
      return;
    }

    try {
      // url을 따로 넘기면 앱에 따라 안내문이 빠지므로 링크를 포함한 text 하나만 넘긴다.
      await navigator.share({ text: guideMessage });
    } catch (shareError) {
      if (isAbortError(shareError)) {
        return;
      }
      setError('안내문을 공유하지 못했어요. 아래 안내문을 직접 복사해 주세요.');
      return;
    }

    onNext();
  }

  async function handleCopy(): Promise<void> {
    setNotice(null);
    setError(null);
    await copyGuideMessage();
  }

  if (loading) {
    return (
      <MobileScreen header={<BrandHeader />}>
        <LoadingMessage />
      </MobileScreen>
    );
  }

  if (loadError) {
    return (
      <MobileScreen header={<BrandHeader />}>
        <div className={styles.errorArea}>
          <ErrorMessage message={loadError} />
          <TextButton onClick={retryLoad}>다시 시도</TextButton>
        </div>
      </MobileScreen>
    );
  }

  return (
    <MobileScreen
      header={<BrandHeader />}
      footer={
        <>
          <PrimaryButton onClick={() => void handleShare()}>
            안내문과 링크 공유하기
          </PrimaryButton>
          <SecondaryButton onClick={() => void handleCopy()}>
            안내문과 링크 복사
          </SecondaryButton>
          {/*
           * 시안은 두 버튼만 두지만 공유·복사가 모두 실패하면 다음 화면으로 갈 수단이 없다.
           * 실패했을 때만 대체 동선을 보여준다.
           */}
          {error ? (
            <TextButton onClick={onNext}>판매자 입력 상태 확인하기</TextButton>
          ) : null}
        </>
      }
    >
      <ResultMessage
        emoji="🤝"
        title="거래가 시작됐어요"
        description={'안내문과 링크를 판매자에게 전달하면\n판매자가 발송 정보를 입력해요.'}
      >
        {showRawMessage ? (
          <p className={styles.guideMessage} data-testid="seller-guide-message">
            {guideMessage}
          </p>
        ) : null}
        {notice ? (
          <p className={styles.notice} role="status">
            {notice}
          </p>
        ) : null}
        {error ? (
          <div className={styles.error}>
            <ErrorMessage message={error} />
          </div>
        ) : null}
      </ResultMessage>
    </MobileScreen>
  );
}
