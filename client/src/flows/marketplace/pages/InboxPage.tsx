import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  getListingMessages,
  getSellerPage,
  MarketplaceApiError,
} from '@/flows/marketplace/api/marketplaceApi';
import type {
  ListingMessage,
  SellerListingSummary,
} from '@/flows/marketplace/model/marketplaceTypes';
import { MarketplaceShell } from '@/flows/marketplace/components';
import AuthGate, { isAuthenticationError } from '@/flows/marketplace/components/AuthGate';
import styles from './SellerPages.module.css';

interface InboxItem extends ListingMessage {
  listing: SellerListingSummary;
}

type InboxState = 'loading' | 'ready' | 'error' | 'authentication-required';

async function fetchInbox(signal?: AbortSignal): Promise<InboxItem[]> {
  const sellerPage = await getSellerPage({ signal });
  const responses = await Promise.all(
    sellerPage.listings.map(async (listing) => ({
      listing,
      messages: (await getListingMessages(listing.id, { signal })).items,
    })),
  );

  return responses
    .flatMap(({ listing, messages }) =>
      messages.map((message) => ({ ...message, listing })),
    )
    .sort(
      (left, right) =>
        new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime(),
    );
}

function formatMessageTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  const elapsedMs = Date.now() - date.getTime();
  const elapsedMinutes = Math.max(0, Math.floor(elapsedMs / 60_000));
  if (elapsedMinutes < 1) return '방금 전';
  if (elapsedMinutes < 60) return `${elapsedMinutes}분 전`;
  if (elapsedMinutes < 1_440) return `${Math.floor(elapsedMinutes / 60)}시간 전`;

  return new Intl.DateTimeFormat('ko-KR', {
    month: 'numeric',
    day: 'numeric',
  }).format(date);
}

function getInboxErrorMessage(error: unknown) {
  if (error instanceof MarketplaceApiError && error.status === 403) {
    return '받은 쪽지를 확인할 권한이 없어요.';
  }
  if (error instanceof MarketplaceApiError && error.status === 404) {
    return '삭제된 매물의 쪽지가 포함되어 있어요. 목록을 다시 불러와 주세요.';
  }
  return error instanceof Error
    ? error.message
    : '쪽지함을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
}

export function InboxPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<InboxItem[]>([]);
  const [state, setState] = useState<InboxState>('loading');
  const [errorMessage, setErrorMessage] = useState('');

  const loadInbox = useCallback(async (signal?: AbortSignal) => {
    setState('loading');
    setErrorMessage('');

    try {
      setItems(await fetchInbox(signal));
      setState('ready');
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') return;
      if (isAuthenticationError(error)) {
        setState('authentication-required');
        return;
      }
      setErrorMessage(getInboxErrorMessage(error));
      setState('error');
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchInbox(controller.signal)
      .then((nextItems) => {
        setItems(nextItems);
        setState('ready');
      })
      .catch((error: unknown) => {
        if (error instanceof Error && error.name === 'AbortError') return;
        if (isAuthenticationError(error)) {
          setState('authentication-required');
          return;
        }
        setErrorMessage(getInboxErrorMessage(error));
        setState('error');
      });
    return () => controller.abort();
  }, []);

  return (
    <AuthGate
      authenticationRequired={state === 'authentication-required'}
      onCancel={() => navigate('/')}
      onAuthenticated={async () => {
        await loadInbox();
      }}
    >
      <MarketplaceShell>
        <main className={styles.page}>
          <section className={styles.pageContent} aria-labelledby="inbox-title">
            <div className={styles.titleBlock}>
              <h1 id="inbox-title">쪽지함</h1>
              <p>구매 희망자가 남긴 익명 문의를 확인해요.</p>
            </div>

            {state === 'loading' && (
              <div className={styles.stateCard} role="status">
                쪽지를 불러오고 있어요…
              </div>
            )}

            {state === 'error' && (
              <div className={styles.stateCard} role="alert">
                <h2>쪽지함을 열지 못했어요</h2>
                <p>{errorMessage}</p>
                <button type="button" onClick={() => void loadInbox()}>
                  다시 시도
                </button>
              </div>
            )}

            {state === 'ready' && items.length === 0 && (
              <div className={styles.stateCard}>
                <h2>아직 받은 쪽지가 없어요</h2>
                <p>내 매물에 문의가 오면 이곳에 모아 보여드려요.</p>
              </div>
            )}

            {state === 'ready' && items.length > 0 && (
              <ul className={styles.messageList} aria-label="받은 쪽지">
                {items.map((item) => (
                  <li
                    key={`${item.listing.id}-${item.id}`}
                    className={styles.messageCard}
                  >
                    {item.listing.thumbnailUrl ? (
                      <img src={item.listing.thumbnailUrl} alt="" />
                    ) : (
                      <span className={styles.imageFallback} aria-hidden="true" />
                    )}
                    <div className={styles.messageBody}>
                      <div className={styles.messageMeta}>
                        <strong>익명 문의</strong>
                        <time dateTime={item.createdAt}>
                          {formatMessageTime(item.createdAt)}
                        </time>
                      </div>
                      <p className={styles.listingName}>{item.listing.title}</p>
                      <p className={styles.messageContent}>{item.content}</p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </main>
      </MarketplaceShell>
    </AuthGate>
  );
}

export default InboxPage;
