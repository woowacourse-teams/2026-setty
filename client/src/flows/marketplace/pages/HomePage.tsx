import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getListings, type ListingSummary } from '@/flows/marketplace/api/marketplaceApi';
import {
  HomeActions,
  ListingCard,
  MarketplaceShell,
  StatusPanel,
} from '@/flows/marketplace/components';
import {
  trackListingDetailOpened,
  type ListingDetailOpenMethod,
} from '@/shared/analytics/googleAnalytics';
import styles from './HomePage.module.css';

const DECK_STORAGE_KEY = 'setty.marketplace.deck.v1';

interface StoredDeck {
  index: number;
  listingIds: number[];
}

function readStoredIndex(listings: ListingSummary[]) {
  try {
    const rawValue = window.sessionStorage.getItem(DECK_STORAGE_KEY);
    if (!rawValue) return 0;

    const stored = JSON.parse(rawValue) as Partial<StoredDeck>;
    const listingIds = listings.map(({ id }) => id);
    const sameDeck =
      Array.isArray(stored.listingIds) &&
      stored.listingIds.length === listingIds.length &&
      stored.listingIds.every((id, index) => id === listingIds[index]);

    if (!sameDeck || typeof stored.index !== 'number') return 0;
    return Math.max(0, Math.min(Math.trunc(stored.index), listings.length));
  } catch {
    return 0;
  }
}

function storeIndex(listings: ListingSummary[], index: number) {
  try {
    window.sessionStorage.setItem(
      DECK_STORAGE_KEY,
      JSON.stringify({
        index,
        listingIds: listings.map(({ id }) => id),
      } satisfies StoredDeck),
    );
  } catch {
    // 저장소가 차단되어도 현재 화면의 덱 사용은 계속할 수 있다.
  }
}

export function HomePage() {
  const navigate = useNavigate();
  const [listings, setListings] = useState<ListingSummary[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadListings = useCallback(async (resetDeck = false) => {
    setIsLoading(true);
    setError(null);

    try {
      const nextListings = await getListings();
      const nextIndex = resetDeck ? 0 : readStoredIndex(nextListings);
      setListings(nextListings);
      setCurrentIndex(nextIndex);
      storeIndex(nextListings, nextIndex);
    } catch {
      setError('매물 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isCurrent = true;

    getListings()
      .then((nextListings) => {
        if (!isCurrent) return;
        const nextIndex = readStoredIndex(nextListings);
        setListings(nextListings);
        setCurrentIndex(nextIndex);
        storeIndex(nextListings, nextIndex);
      })
      .catch(() => {
        if (!isCurrent) return;
        setError('매물 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
      })
      .finally(() => {
        if (isCurrent) setIsLoading(false);
      });

    return () => {
      isCurrent = false;
    };
  }, []);

  const currentListing = listings[currentIndex];

  const moveToIndex = (nextIndex: number) => {
    const safeIndex = Math.max(0, Math.min(nextIndex, listings.length));
    setCurrentIndex(safeIndex);
    storeIndex(listings, safeIndex);
  };

  const advance = () => {
    if (!currentListing) return;
    moveToIndex(currentIndex + 1);
  };

  const openDetail = (consume: boolean, detailOpenMethod: ListingDetailOpenMethod) => {
    if (!currentListing) return;
    trackListingDetailOpened(currentListing.id, detailOpenMethod);
    if (consume) advance();

    navigate(`/listings/${currentListing.id}`, {
      state: { from: '/', marketplaceDeckConsumed: consume },
    });
  };

  const openMessage = () => {
    if (!currentListing) return;
    navigate(`/listings/${currentListing.id}/message`, {
      state: { from: '/', marketplaceDeckConsumed: false },
    });
  };

  const deckContent = (() => {
    if (isLoading) {
      return (
        <StatusPanel
          description="가까운 동네의 새 매물을 찾고 있어요."
          title="가구를 불러오는 중이에요"
        />
      );
    }

    if (error) {
      return (
        <StatusPanel
          actionLabel="다시 시도"
          description={error}
          onAction={() => void loadListings()}
          title="목록을 열지 못했어요"
          variant="error"
        />
      );
    }

    if (!currentListing) {
      return (
        <StatusPanel
          actionLabel="다시 보기"
          description={'처음부터 다시 보거나,\n안 쓰는 가구를 올려보세요.'}
          onAction={() => void loadListings(true)}
          title="오늘 동네 가구는 끝"
        />
      );
    }

    return (
      <ListingCard
        key={currentListing.id}
        listing={currentListing}
        onOpen={() => openDetail(false, 'card_tap')}
        onSwipeLeft={advance}
        onSwipeRight={() => openDetail(true, 'swipe_right')}
      />
    );
  })();

  return (
    <MarketplaceShell>
      <main className={styles.main}>
        <div className={styles.deck}>
          {deckContent}
          {!isLoading && !error && currentListing ? (
            <p aria-live="polite" className={styles.position}>
              {currentIndex + 1} / {listings.length}
            </p>
          ) : null}
        </div>

        <HomeActions
          canUndo={!isLoading && !error && currentIndex > 0}
          disabled={!currentListing || isLoading || Boolean(error)}
          onDetail={() => openDetail(true, 'detail_button')}
          onMessage={openMessage}
          onSkip={advance}
          onUndo={() => moveToIndex(currentIndex - 1)}
        />
      </main>
    </MarketplaceShell>
  );
}

export default HomePage;
