import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  deleteListing,
  getSellerPage,
  logout,
  MarketplaceApiError,
} from '@/flows/marketplace/api/marketplaceApi';
import type {
  SellerListingSummary,
  SellerPageResponse,
} from '@/flows/marketplace/model/marketplaceTypes';
import { MarketplaceShell } from '@/flows/marketplace/components';
import AuthGate, { isAuthenticationError } from '@/flows/marketplace/components/AuthGate';
import styles from './SellerPages.module.css';

type MineState = 'loading' | 'ready' | 'error' | 'authentication-required';
type AuthPurpose = 'initial' | 'mutation';

interface MarketplaceRouteState {
  marketplaceNotice?: string;
}

function getActionError(error: unknown, action: 'load' | 'delete' | 'logout') {
  if (error instanceof MarketplaceApiError) {
    if (error.status === 403) return '이 매물을 삭제할 권한이 없어요.';
    if (error.status === 404) return '이미 삭제되었거나 존재하지 않는 매물이에요.';
  }

  if (error instanceof Error) return error.message;
  if (action === 'delete') return '매물을 삭제하지 못했어요.';
  if (action === 'logout') return '로그아웃하지 못했어요.';
  return '내 매물을 불러오지 못했어요.';
}

export function MinePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const routeState = location.state as MarketplaceRouteState | null;
  const [sellerPage, setSellerPage] = useState<SellerPageResponse | null>(null);
  const [state, setState] = useState<MineState>('loading');
  const [errorMessage, setErrorMessage] = useState('');
  const [notice, setNotice] = useState(routeState?.marketplaceNotice ?? '');
  const [authPurpose, setAuthPurpose] = useState<AuthPurpose>('initial');
  const [deleteTarget, setDeleteTarget] = useState<SellerListingSummary | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const deleteDialogRef = useRef<HTMLElement>(null);
  const deleteCancelRef = useRef<HTMLButtonElement>(null);
  const deleteTriggerRef = useRef<HTMLButtonElement>(null);

  const loadMine = useCallback(async (signal?: AbortSignal) => {
    setState('loading');
    setErrorMessage('');
    try {
      const response = await getSellerPage({ signal });
      setSellerPage(response);
      setState('ready');
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') return;
      if (isAuthenticationError(error)) {
        setAuthPurpose('initial');
        setState('authentication-required');
        return;
      }
      setErrorMessage(getActionError(error, 'load'));
      setState('error');
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    getSellerPage({ signal: controller.signal })
      .then((response) => {
        setSellerPage(response);
        setState('ready');
      })
      .catch((error: unknown) => {
        if (error instanceof Error && error.name === 'AbortError') return;
        if (isAuthenticationError(error)) {
          setAuthPurpose('initial');
          setState('authentication-required');
          return;
        }
        setErrorMessage(getActionError(error, 'load'));
        setState('error');
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!deleteTarget) return;
    const focusFrame = window.requestAnimationFrame(() =>
      deleteCancelRef.current?.focus(),
    );

    return () => {
      window.cancelAnimationFrame(focusFrame);
      deleteTriggerRef.current?.focus();
    };
  }, [deleteTarget]);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setErrorMessage('');

    try {
      await deleteListing(deleteTarget.id);
      setDeleteTarget(null);
      setNotice('매물을 삭제했어요.');
      await loadMine();
    } catch (error) {
      setDeleteTarget(null);
      if (isAuthenticationError(error)) {
        setAuthPurpose('mutation');
        setState('authentication-required');
      } else {
        setErrorMessage(getActionError(error, 'delete'));
      }
    } finally {
      setIsDeleting(false);
    }
  };

  const handleLogout = async () => {
    setIsLoggingOut(true);
    setErrorMessage('');
    try {
      await logout();
      navigate('/', { replace: true });
    } catch (error) {
      if (isAuthenticationError(error)) {
        navigate('/', { replace: true });
      } else {
        setErrorMessage(getActionError(error, 'logout'));
      }
    } finally {
      setIsLoggingOut(false);
    }
  };

  return (
    <AuthGate
      authenticationRequired={state === 'authentication-required'}
      onCancel={() => {
        if (authPurpose === 'initial') navigate('/');
        else setState(sellerPage ? 'ready' : 'error');
      }}
      onAuthenticated={async () => {
        if (authPurpose === 'mutation') {
          setNotice('로그인했어요. 변경 작업을 다시 실행해 주세요.');
        }
        await loadMine();
      }}
    >
      <div
        aria-hidden={Boolean(deleteTarget) || undefined}
        inert={Boolean(deleteTarget) || undefined}
      >
        <MarketplaceShell>
          <main className={styles.page}>
            <section className={styles.pageContent} aria-labelledby="mine-title">
              <div className={styles.titleRow}>
                <div className={styles.titleBlock}>
                  <h1 id="mine-title">내 매물</h1>
                  <p>내가 올린 가구와 받은 문의를 관리해요.</p>
                </div>
                <button
                  className={styles.logoutButton}
                  type="button"
                  disabled={isLoggingOut}
                  onClick={() => void handleLogout()}
                >
                  {isLoggingOut ? '처리 중…' : '로그아웃'}
                </button>
              </div>

              <button
                className={styles.createButton}
                type="button"
                onClick={() => navigate('/mine/new')}
              >
                <span aria-hidden="true">＋</span> 가구 올리기
              </button>

              {notice && (
                <p className={styles.notice} role="status">
                  {notice}
                </p>
              )}
              {errorMessage && state !== 'error' && (
                <p className={styles.inlineError} role="alert">
                  {errorMessage}
                </p>
              )}

              {state === 'loading' && (
                <div className={styles.stateCard} role="status">
                  내 매물을 불러오고 있어요…
                </div>
              )}

              {state === 'error' && (
                <div className={styles.stateCard} role="alert">
                  <h2>내 매물을 불러오지 못했어요</h2>
                  <p>{errorMessage}</p>
                  <button type="button" onClick={() => void loadMine()}>
                    다시 시도
                  </button>
                </div>
              )}

              {state === 'ready' && sellerPage?.listings.length === 0 && (
                <div className={styles.stateCard}>
                  <h2>아직 올린 가구가 없어요</h2>
                  <p>첫 가구를 올리고 새로운 주인을 찾아보세요.</p>
                </div>
              )}

              {state === 'ready' && sellerPage && sellerPage.listings.length > 0 && (
                <ul className={styles.listingList} aria-label="내 매물 목록">
                  {sellerPage.listings.map((listing) => (
                    <li className={styles.listingCard} key={listing.id}>
                      <div className={styles.listingSummary}>
                        {listing.thumbnailUrl ? (
                          <img src={listing.thumbnailUrl} alt="" />
                        ) : (
                          <span className={styles.imageFallback} aria-hidden="true" />
                        )}
                        <div>
                          <h2>{listing.title}</h2>
                          <p>{listing.pickupTimeText}</p>
                          <strong>쪽지 {listing.messageCount}통</strong>
                        </div>
                      </div>
                      <div className={styles.listingActions}>
                        <button
                          type="button"
                          onClick={() => navigate(`/mine/${listing.id}/edit`)}
                        >
                          수정
                        </button>
                        <button
                          className={styles.dangerButton}
                          type="button"
                          onClick={(event) => {
                            deleteTriggerRef.current = event.currentTarget;
                            setDeleteTarget(listing);
                          }}
                        >
                          삭제
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </main>
        </MarketplaceShell>
      </div>

      {deleteTarget && (
        <div
          className={styles.confirmBackdrop}
          role="presentation"
          onKeyDown={(event) => {
            if (event.key === 'Escape' && !isDeleting) {
              setDeleteTarget(null);
              return;
            }

            if (event.key === 'Tab') {
              const focusableElements = Array.from(
                deleteDialogRef.current?.querySelectorAll<HTMLButtonElement>(
                  'button:not([disabled])',
                ) ?? [],
              );
              const firstElement = focusableElements[0];
              const lastElement = focusableElements.at(-1);
              if (!firstElement || !lastElement) {
                event.preventDefault();
                deleteDialogRef.current?.focus();
                return;
              }

              if (event.shiftKey && document.activeElement === firstElement) {
                event.preventDefault();
                lastElement.focus();
              } else if (!event.shiftKey && document.activeElement === lastElement) {
                event.preventDefault();
                firstElement.focus();
              }
            }
          }}
        >
          <section
            ref={deleteDialogRef}
            className={styles.confirmDialog}
            role="alertdialog"
            tabIndex={-1}
            aria-modal="true"
            aria-labelledby="delete-listing-title"
            aria-describedby="delete-listing-description"
          >
            <h2 id="delete-listing-title">이 매물을 삭제할까요?</h2>
            <p id="delete-listing-description">
              “{deleteTarget.title}”과 연결된 쪽지도 함께 삭제되며 되돌릴 수 없어요.
            </p>
            <div>
              <button
                ref={deleteCancelRef}
                type="button"
                disabled={isDeleting}
                onClick={() => setDeleteTarget(null)}
              >
                취소
              </button>
              <button
                className={styles.confirmDangerButton}
                type="button"
                disabled={isDeleting}
                onClick={() => void handleDelete()}
              >
                {isDeleting ? '삭제 중…' : '삭제'}
              </button>
            </div>
          </section>
        </div>
      )}
    </AuthGate>
  );
}

export default MinePage;
