import { type FormEvent, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  getListingDetail,
  sendListingMessage,
} from '@/flows/marketplace/api/marketplaceApi';
import styles from './MessageComposePage.module.css';

const MAX_MESSAGE_LENGTH = 500;

function getMessage(error: unknown) {
  return error instanceof Error ? error.message : '쪽지를 보내지 못했어요.';
}

export default function MessageComposePage() {
  const { listingId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const id = Number(listingId);
  const initialTitle = (location.state as { title?: string } | null)?.title;
  const [title, setTitle] = useState(initialTitle ?? '이 가구');
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSent, setIsSent] = useState(false);
  const closeTimerRef = useRef<number | null>(null);

  useEffect(() => {
    if (initialTitle || !Number.isSafeInteger(id) || id <= 0) return;
    const controller = new AbortController();
    void getListingDetail(id, { signal: controller.signal })
      .then((listing) => setTitle(listing.title))
      .catch((loadError: unknown) => {
        if (loadError instanceof DOMException && loadError.name === 'AbortError') return;
      });
    return () => controller.abort();
  }, [id, initialTitle]);

  useEffect(
    () => () => {
      if (closeTimerRef.current !== null) {
        window.clearTimeout(closeTimerRef.current);
      }
    },
    [],
  );

  const close = () => {
    if (closeTimerRef.current !== null) {
      window.clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
    if (location.key === 'default') {
      navigate(`/listings/${listingId}`, { replace: true });
      return;
    }
    navigate(-1);
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = content.trim();
    if (!Number.isSafeInteger(id) || id <= 0) {
      setError('올바르지 않은 매물 주소예요.');
      return;
    }
    if (trimmed.length === 0 || trimmed.length > MAX_MESSAGE_LENGTH) {
      setError(`쪽지는 1자 이상 ${MAX_MESSAGE_LENGTH}자 이하로 입력해 주세요.`);
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      await sendListingMessage(id, { content: trimmed });
      setIsSent(true);
      closeTimerRef.current = window.setTimeout(close, 800);
    } catch (sendError) {
      setError(getMessage(sendError));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className={styles.overlay}>
      <div className={styles.scrim} aria-hidden="true" />
      <section className={styles.sheet} aria-labelledby="message-heading">
        <div className={styles.handle} aria-hidden="true" />
        <h1 id="message-heading">쪽지 보내기</h1>
        <p className={styles.subtitle}>{title} · 익명 문의로 전달돼요</p>

        <form onSubmit={(event) => void submit(event)}>
          <label className={styles.textareaLabel}>
            <span className={styles.visuallyHidden}>쪽지 내용</span>
            <textarea
              autoFocus
              value={content}
              maxLength={MAX_MESSAGE_LENGTH}
              placeholder="픽업 가능한 시간이나 궁금한 점을 남겨보세요."
              onChange={(event) => setContent(event.target.value)}
            />
          </label>
          <div className={styles.counter}>
            {content.length} / {MAX_MESSAGE_LENGTH}
          </div>

          {error && (
            <p className={styles.error} role="alert">
              {error}
            </p>
          )}
          {isSent && (
            <p className={styles.success} role="status">
              판매자에게 쪽지를 남겼어요.
            </p>
          )}

          <div className={styles.actions}>
            <button
              type="button"
              className={styles.cancelButton}
              onClick={close}
              disabled={isSubmitting}
            >
              취소
            </button>
            <button
              type="submit"
              className={styles.sendButton}
              disabled={isSubmitting || isSent}
            >
              {isSubmitting ? '보내는 중…' : '보내기'}
            </button>
          </div>
        </form>
      </section>
    </main>
  );
}
