import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getListingDetail } from '@/flows/marketplace/api/marketplaceApi';
import type { ListingDetailResponse } from '@/flows/marketplace/model/marketplaceTypes';
import { formatListingPrice } from '@/flows/marketplace/model/listingPrice';
import styles from './ListingDetailPage.module.css';

const HTTP_URL_PATTERN = /(https?:\/\/[^\s]+)/g;

function LinkedDescription({ text }: { text: string }) {
  return (
    <p className={styles.description}>
      {text.split(HTTP_URL_PATTERN).map((part, index) => {
        if (!part.startsWith('http://') && !part.startsWith('https://')) {
          return <span key={`${index}-${part}`}>{part}</span>;
        }

        try {
          const url = new URL(part);
          if (url.protocol !== 'http:' && url.protocol !== 'https:') {
            return <span key={`${index}-${part}`}>{part}</span>;
          }

          return (
            <a
              key={`${index}-${part}`}
              className={styles.inlineLink}
              href={url.toString()}
              rel="noopener noreferrer"
              target="_blank"
            >
              {part}
            </a>
          );
        } catch {
          return <span key={`${index}-${part}`}>{part}</span>;
        }
      })}
    </p>
  );
}

function getMessage(error: unknown) {
  return error instanceof Error ? error.message : '매물 정보를 불러오지 못했어요.';
}

export default function ListingDetailPage() {
  const { listingId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const id = Number(listingId);
  const isValidId = Number.isSafeInteger(id) && id > 0;
  const [listing, setListing] = useState<ListingDetailResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeImageIndex, setActiveImageIndex] = useState(0);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    if (!isValidId) return;
    const controller = new AbortController();

    void getListingDetail(id, { signal: controller.signal })
      .then((response) => {
        setListing(response);
        setActiveImageIndex(0);
      })
      .catch((loadError: unknown) => {
        if (loadError instanceof DOMException && loadError.name === 'AbortError') return;
        setError(getMessage(loadError));
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, [id, isValidId, retryCount]);

  const close = () => {
    if (location.key === 'default') {
      navigate('/', { replace: true });
      return;
    }
    navigate(-1);
  };

  if (!isValidId) {
    return (
      <main className={styles.page}>
        <section className={styles.stateCard}>
          <p>올바르지 않은 매물 주소예요.</p>
          <button type="button" className={styles.primaryButton} onClick={close}>
            가구 둘러보기
          </button>
        </section>
      </main>
    );
  }

  if (isLoading) {
    return (
      <main className={styles.page} aria-busy="true">
        <div className={styles.loadingCard}>가구 이야기를 불러오는 중이에요…</div>
      </main>
    );
  }

  if (error || !listing) {
    return (
      <main className={styles.page}>
        <section className={styles.stateCard}>
          <p>{error ?? '매물을 찾을 수 없어요.'}</p>
          <div className={styles.stateActions}>
            <button type="button" className={styles.outlineButton} onClick={close}>
              돌아가기
            </button>
            <button
              type="button"
              className={styles.primaryButton}
              onClick={() => {
                setIsLoading(true);
                setError(null);
                setRetryCount((count) => count + 1);
              }}
            >
              다시 시도
            </button>
          </div>
        </section>
      </main>
    );
  }

  const images = [...listing.images].sort((a, b) => a.displayOrder - b.displayOrder);
  const activeImage = images[activeImageIndex];

  return (
    <main className={styles.page}>
      <section className={styles.detailSheet} aria-labelledby="listing-title">
        <div className={styles.imageArea}>
          {activeImage ? (
            <img
              src={activeImage.url}
              alt={`${listing.title} 사진 ${activeImageIndex + 1}`}
            />
          ) : (
            <div className={styles.imageFallback}>등록된 사진이 없어요</div>
          )}
          {images.length > 1 && (
            <div
              className={styles.imageProgress}
              aria-label={`사진 ${activeImageIndex + 1}/${images.length}`}
            >
              {images.map((image, index) => (
                <button
                  key={image.id}
                  type="button"
                  className={
                    index === activeImageIndex ? styles.activeProgress : undefined
                  }
                  aria-label={`${index + 1}번째 사진 보기`}
                  aria-current={index === activeImageIndex ? 'true' : undefined}
                  onClick={() => setActiveImageIndex(index)}
                />
              ))}
            </div>
          )}
        </div>

        <div className={styles.content}>
          <p className={styles.eyebrow}>동네에서 만난 가구</p>
          <h1 id="listing-title">{listing.title}</h1>
          <p className={styles.price}>{formatListingPrice(listing.price)}</p>
          <LinkedDescription text={listing.description} />

          <dl className={styles.infoList}>
            <div>
              <dt>픽업 가능 시간</dt>
              <dd>{listing.pickupTimeText}</dd>
            </div>
            <div>
              <dt>같이 옮겨주기</dt>
              <dd>{listing.canHelpMove ? '가능해요' : '어려워요'}</dd>
            </div>
          </dl>

          <p className={styles.privacyNote}>
            판매자 연락처는 공개되지 않아요. 익명 쪽지로 먼저 의사를 남겨주세요.
          </p>
        </div>

        <div className={styles.bottomActions}>
          <button type="button" className={styles.outlineButton} onClick={close}>
            닫기
          </button>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={() =>
              navigate(`/listings/${listing.id}/message`, {
                state: { title: listing.title },
              })
            }
          >
            쪽지 보내기
          </button>
        </div>
      </section>
    </main>
  );
}
