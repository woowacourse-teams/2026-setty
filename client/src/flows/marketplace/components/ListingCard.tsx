import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import type { ListingSummary } from '@/flows/marketplace/api/marketplaceApi';
import styles from './ListingCard.module.css';

const SWIPE_THRESHOLD = 90;

interface ListingCardProps {
  listing: ListingSummary;
  onOpen: () => void;
  onSwipeLeft: () => void;
  onSwipeRight: () => void;
}

interface PointerOrigin {
  id: number;
  x: number;
}

export default function ListingCard({
  listing,
  onOpen,
  onSwipeLeft,
  onSwipeRight,
}: ListingCardProps) {
  const [dragX, setDragX] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const [imageFailed, setImageFailed] = useState(false);
  const pointerOrigin = useRef<PointerOrigin | null>(null);
  const suppressClick = useRef(false);

  const releaseCard = (event: PointerEvent<HTMLElement>) => {
    const origin = pointerOrigin.current;
    if (!origin || origin.id !== event.pointerId) return;

    const offset = event.clientX - origin.x;
    suppressClick.current = Math.abs(offset) > 8;
    pointerOrigin.current = null;
    setIsDragging(false);

    if (offset <= -SWIPE_THRESHOLD) {
      setDragX(-window.innerWidth);
      onSwipeLeft();
      return;
    }

    if (offset >= SWIPE_THRESHOLD) {
      setDragX(window.innerWidth);
      onSwipeRight();
      return;
    }

    setDragX(0);
  };

  const cancelDrag = () => {
    pointerOrigin.current = null;
    suppressClick.current = true;
    setIsDragging(false);
    setDragX(0);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    onOpen();
  };

  const handleClick = () => {
    if (suppressClick.current) {
      suppressClick.current = false;
      return;
    }
    onOpen();
  };

  const tilt = Math.max(-7, Math.min(7, dragX / 25));
  const approveOpacity = Math.max(0, Math.min(1, dragX / SWIPE_THRESHOLD));
  const skipOpacity = Math.max(0, Math.min(1, -dragX / SWIPE_THRESHOLD));

  return (
    <article
      aria-label={`${listing.title} 상세 보기`}
      className={`${styles.card} ${isDragging ? styles.dragging : ''}`}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      onPointerCancel={cancelDrag}
      onPointerDown={(event) => {
        if (event.isPrimary === false || event.button !== 0) return;
        pointerOrigin.current = { id: event.pointerId, x: event.clientX };
        suppressClick.current = false;
        setIsDragging(true);
        event.currentTarget.setPointerCapture?.(event.pointerId);
      }}
      onPointerMove={(event) => {
        const origin = pointerOrigin.current;
        if (!origin || origin.id !== event.pointerId) return;
        setDragX(event.clientX - origin.x);
      }}
      onPointerUp={releaseCard}
      role="button"
      style={{ transform: `translate3d(${dragX}px, 0, 0) rotate(${tilt}deg)` }}
      tabIndex={0}
    >
      <div
        aria-hidden="true"
        className={styles.approveStamp}
        style={{ opacity: approveOpacity }}
      >
        가져갈게요
      </div>
      <div
        aria-hidden="true"
        className={styles.skipStamp}
        style={{ opacity: skipOpacity }}
      >
        다음에
      </div>

      <div className={styles.imagePlaceholder}>
        <span aria-hidden="true">세티</span>
      </div>
      {!imageFailed ? (
        <img
          alt={`${listing.title} 대표 사진`}
          className={styles.image}
          draggable={false}
          onError={() => setImageFailed(true)}
          src={listing.thumbnailUrl}
        />
      ) : null}
      <div className={styles.overlay} />

      <div className={styles.content}>
        <p className={styles.eyebrow}>오늘의 동네 가구</p>
        <h1 className={styles.title}>{listing.title}</h1>
        <dl className={styles.details}>
          <div className={styles.detailRow}>
            <dt>픽업</dt>
            <dd>{listing.pickupTimeText}</dd>
          </div>
          <div className={styles.detailRow}>
            <dt>운반</dt>
            <dd>
              {listing.canHelpMove
                ? '판매자가 운반을 도와드릴 수 있어요'
                : '직접 운반이 필요해요'}
            </dd>
          </div>
        </dl>
        <span className={styles.detailHint}>카드를 눌러 자세히 보기</span>
      </div>
    </article>
  );
}
