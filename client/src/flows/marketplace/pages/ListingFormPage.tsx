import {
  ChangeEvent,
  FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  createListing,
  getListingDetail,
  getSellerPage,
  MarketplaceApiError,
  updateListing,
} from '@/flows/marketplace/api/marketplaceApi';
import type {
  ListingDetailResponse,
  ListingDraft,
  UpdateListingRequest,
} from '@/flows/marketplace/model/marketplaceTypes';
import { MarketplaceShell } from '@/flows/marketplace/components';
import {
  MAX_LISTING_IMAGE_COUNT,
  MAX_LISTING_IMAGE_TOTAL_BYTES,
  SUPPORTED_LISTING_IMAGE_TYPES,
  type ListingValidationErrors,
  validateListingDraft,
  validateListingImages,
} from '@/flows/marketplace/model/marketplaceValidation';
import AuthGate, { isAuthenticationError } from '@/flows/marketplace/components/AuthGate';
import {
  trackListingCreated,
  trackListingCreateStarted,
} from '@/shared/analytics/googleAnalytics';
import styles from './SellerPages.module.css';

type FormLoadState = 'loading' | 'ready' | 'error' | 'authentication-required';
type AuthPurpose = 'initial' | 'mutation';

const EMPTY_DRAFT: ListingDraft = {
  title: '',
  description: '',
  // NaN은 "아직 입력하지 않음"을 뜻하며, 제출 시 검증에서 걸러진다.
  price: Number.NaN,
  pickupTimeText: '',
  canHelpMove: false,
};

const MAX_PRICE_INPUT_LENGTH = 9;

/** 숫자만 남긴 가격 입력을 number로 바꾼다. 비어 있으면 NaN(미입력)으로 둔다. */
function parsePriceInput(rawValue: string): number {
  const digitsOnly = rawValue.replace(/\D/g, '').slice(0, MAX_PRICE_INPUT_LENGTH);
  return digitsOnly === '' ? Number.NaN : Number(digitsOnly);
}

/** 폼 입력창에 그대로 보여줄 문자열. NaN(미입력)이면 빈 문자열이다. */
function priceInputValue(price: number): string {
  return Number.isNaN(price) ? '' : String(price);
}

function getSubmitError(error: unknown) {
  if (error instanceof MarketplaceApiError) {
    if (error.status === 403) return '이 매물을 수정할 권한이 없어요.';
    if (error.status === 404) return '이미 삭제되었거나 존재하지 않는 매물이에요.';
    if (error.status === 413) return '사진 전체 용량은 25MB 이하여야 해요.';
    if (error.status === 415) return 'JPEG, PNG, WebP 사진만 올릴 수 있어요.';
  }
  return error instanceof Error
    ? error.message
    : '매물을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.';
}

function formatMegabytes(bytes: number) {
  if (bytes === 0) return '0MB';
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

function getListingChanges(
  next: ListingDraft,
  current: ListingDetailResponse,
): UpdateListingRequest | undefined {
  const changes: Partial<ListingDraft> = {};

  if (next.title !== current.title) changes.title = next.title;
  if (next.description !== current.description) changes.description = next.description;
  if (next.price !== (current.price ?? Number.NaN)) changes.price = next.price;
  if (next.pickupTimeText !== current.pickupTimeText) {
    changes.pickupTimeText = next.pickupTimeText;
  }
  if (next.canHelpMove !== current.canHelpMove) {
    changes.canHelpMove = next.canHelpMove;
  }

  return Object.keys(changes).length > 0 ? (changes as UpdateListingRequest) : undefined;
}

async function fetchProtectedForm(
  isEditing: boolean,
  listingId: number | null,
  signal?: AbortSignal,
) {
  const sellerPage = await getSellerPage({ signal });
  if (!isEditing || listingId === null) return null;

  if (!sellerPage.listings.some((listing) => listing.id === listingId)) {
    throw new MarketplaceApiError(403, {
      code: 'LISTING_ACCESS_DENIED',
      message: '이 매물을 수정할 권한이 없어요.',
    });
  }

  return getListingDetail(listingId, { signal });
}

interface SelectedImageGridProps {
  images: readonly File[];
  onRemove: (index: number) => void;
}

function SelectedImageGrid({ images, onRemove }: SelectedImageGridProps) {
  const previews = useMemo(
    () =>
      images.map((image) => ({
        image,
        url: typeof URL.createObjectURL === 'function' ? URL.createObjectURL(image) : '',
      })),
    [images],
  );

  useEffect(
    () => () => {
      previews.forEach(({ url }) => {
        if (url && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(url);
      });
    },
    [previews],
  );

  return (
    <>
      {previews.map(({ image, url }, index) => (
        <div
          className={styles.photoPreview}
          key={`${image.name}-${image.lastModified}-${index}`}
        >
          {url ? (
            <img src={url} alt={`선택한 사진 ${index + 1}`} />
          ) : (
            <span>{image.name}</span>
          )}
          <button
            type="button"
            aria-label={`${index + 1}번째 사진 제거`}
            onClick={() => onRemove(index)}
          >
            ×
          </button>
        </div>
      ))}
    </>
  );
}

export function ListingFormPage() {
  const navigate = useNavigate();
  const params = useParams<{ listingId?: string; id?: string }>();
  const rawListingId = params.listingId ?? params.id;
  const listingId = rawListingId === undefined ? null : Number(rawListingId);
  const isEditing = rawListingId !== undefined;
  const hasInvalidListingId =
    isEditing && (!Number.isSafeInteger(listingId) || (listingId ?? 0) <= 0);
  const [loadState, setLoadState] = useState<FormLoadState>(
    hasInvalidListingId ? 'error' : 'loading',
  );
  const [authPurpose, setAuthPurpose] = useState<AuthPurpose>('initial');
  const [draft, setDraft] = useState<ListingDraft>(EMPTY_DRAFT);
  const [existingListing, setExistingListing] = useState<ListingDetailResponse | null>(
    null,
  );
  const [images, setImages] = useState<File[]>([]);
  const [errors, setErrors] = useState<ListingValidationErrors>({});
  const [loadError, setLoadError] = useState(
    hasInvalidListingId ? '올바르지 않은 매물 주소예요.' : '',
  );
  const [submitError, setSubmitError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const createStartedTrackedRef = useRef(false);

  const trackCreateStartedOnce = () => {
    if (isEditing || createStartedTrackedRef.current) return;
    createStartedTrackedRef.current = true;
    trackListingCreateStarted();
  };

  const loadProtectedForm = useCallback(
    async (signal?: AbortSignal) => {
      if (hasInvalidListingId) {
        setLoadError('올바르지 않은 매물 주소예요.');
        setLoadState('error');
        return;
      }

      setLoadState('loading');
      setLoadError('');
      try {
        const listing = await fetchProtectedForm(isEditing, listingId, signal);
        if (listing) {
          setExistingListing(listing);
          setDraft({
            title: listing.title,
            description: listing.description,
            price: listing.price ?? Number.NaN,
            pickupTimeText: listing.pickupTimeText,
            canHelpMove: listing.canHelpMove,
          });
        }
        setLoadState('ready');
      } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') return;
        if (isAuthenticationError(error)) {
          setAuthPurpose('initial');
          setLoadState('authentication-required');
          return;
        }
        setLoadError(getSubmitError(error));
        setLoadState('error');
      }
    },
    [hasInvalidListingId, isEditing, listingId],
  );

  useEffect(() => {
    if (hasInvalidListingId) return;

    const controller = new AbortController();
    fetchProtectedForm(isEditing, listingId, controller.signal)
      .then((listing) => {
        if (listing) {
          setExistingListing(listing);
          setDraft({
            title: listing.title,
            description: listing.description,
            price: listing.price ?? Number.NaN,
            pickupTimeText: listing.pickupTimeText,
            canHelpMove: listing.canHelpMove,
          });
        }
        setLoadState('ready');
      })
      .catch((error: unknown) => {
        if (error instanceof Error && error.name === 'AbortError') return;
        if (isAuthenticationError(error)) {
          setAuthPurpose('initial');
          setLoadState('authentication-required');
          return;
        }
        setLoadError(getSubmitError(error));
        setLoadState('error');
      });
    return () => controller.abort();
  }, [hasInvalidListingId, isEditing, listingId]);

  const updateDraft = <Field extends keyof ListingDraft>(
    field: Field,
    value: ListingDraft[Field],
  ) => {
    trackCreateStartedOnce();
    setDraft((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setSubmitError('');
  };

  const handleImageSelection = (event: ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(event.target.files ?? []);
    event.target.value = '';
    if (selected.length === 0) return;
    trackCreateStartedOnce();

    const nextImages = [...images, ...selected];
    const imageError = validateListingImages(nextImages);
    if (imageError) {
      setErrors((current) => ({ ...current, images: imageError }));
      return;
    }

    setImages(nextImages);
    setErrors((current) => ({ ...current, images: undefined }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitError('');

    const nextErrors = validateListingDraft(draft);
    if (!isEditing) {
      const imagesError = validateListingImages(images);
      if (imagesError) nextErrors.images = imagesError;
    }
    setErrors(nextErrors);
    if (Object.values(nextErrors).some(Boolean)) return;

    const trimmedDraft: ListingDraft = {
      title: draft.title.trim(),
      description: draft.description.trim(),
      price: draft.price,
      pickupTimeText: draft.pickupTimeText.trim(),
      canHelpMove: draft.canHelpMove,
    };

    const listingChanges =
      isEditing && existingListing
        ? getListingChanges(trimmedDraft, existingListing)
        : undefined;
    if (isEditing && !listingChanges) {
      setSubmitError('변경된 내용을 입력해 주세요.');
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEditing) {
        if (listingId === null || !listingChanges) return;
        await updateListing(listingId, listingChanges);
      } else {
        const createdListing = await createListing({ ...trimmedDraft, images });
        trackListingCreated(createdListing.listingId, trimmedDraft.price);
      }
      navigate('/mine', {
        replace: true,
        state: {
          marketplaceNotice: isEditing ? '매물을 수정했어요.' : '매물을 등록했어요.',
        },
      });
    } catch (error) {
      if (isAuthenticationError(error)) {
        setAuthPurpose('mutation');
        setLoadState('authentication-required');
      } else {
        setSubmitError(getSubmitError(error));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const sortedExistingImages = [...(existingListing?.images ?? [])].sort(
    (left, right) => left.displayOrder - right.displayOrder,
  );
  const totalImageBytes = images.reduce((sum, image) => sum + image.size, 0);

  return (
    <AuthGate
      authenticationRequired={loadState === 'authentication-required'}
      onCancel={() => {
        if (authPurpose === 'initial') navigate('/');
        else setLoadState('ready');
      }}
      onAuthenticated={async () => {
        if (authPurpose === 'initial') {
          await loadProtectedForm();
          return;
        }
        setLoadState('ready');
        setSubmitError('로그인했어요. 저장 버튼을 다시 눌러 주세요.');
      }}
    >
      <MarketplaceShell showHeader={false}>
        <main className={styles.formPage}>
          <section className={styles.formSheet} aria-labelledby="listing-form-title">
            <header className={styles.formHeader}>
              <button
                type="button"
                onClick={() => navigate('/mine')}
                aria-label="내 매물로 돌아가기"
              >
                ←
              </button>
              <div>
                <h1 id="listing-form-title">
                  {isEditing ? '가구 수정하기' : '가구 올리기'}
                </h1>
                <p>
                  {isEditing
                    ? '사진은 그대로 두고 가구 정보를 수정할 수 있어요.'
                    : '사진 1~5장, 총 25MB까지 올릴 수 있어요.'}
                </p>
              </div>
            </header>

            {loadState === 'loading' && (
              <div className={styles.stateCard} role="status">
                {isEditing
                  ? '매물 정보를 불러오고 있어요…'
                  : '등록 화면을 준비하고 있어요…'}
              </div>
            )}

            {loadState === 'error' && (
              <div className={styles.stateCard} role="alert">
                <h2>등록 화면을 열지 못했어요</h2>
                <p>{loadError}</p>
                {!hasInvalidListingId && (
                  <button type="button" onClick={() => void loadProtectedForm()}>
                    다시 시도
                  </button>
                )}
              </div>
            )}

            {loadState === 'ready' && (
              <form
                className={styles.listingForm}
                onSubmit={(event) => void handleSubmit(event)}
                noValidate
              >
                <fieldset className={styles.photoFieldset}>
                  <legend>사진</legend>
                  {isEditing ? (
                    <>
                      <div className={styles.photoGrid}>
                        {sortedExistingImages.map((image, index) => (
                          <div className={styles.photoPreview} key={image.id}>
                            <img src={image.url} alt={`등록된 사진 ${index + 1}`} />
                          </div>
                        ))}
                      </div>
                      <p className={styles.fieldHint}>
                        이번 버전에서는 기존 사진을 바꿀 수 없어요.
                      </p>
                    </>
                  ) : (
                    <>
                      <div className={styles.photoGrid}>
                        <SelectedImageGrid
                          images={images}
                          onRemove={(targetIndex) => {
                            setImages((current) =>
                              current.filter((_, index) => index !== targetIndex),
                            );
                            setErrors((current) => ({ ...current, images: undefined }));
                          }}
                        />
                        {images.length < MAX_LISTING_IMAGE_COUNT && (
                          <label className={styles.photoAddButton}>
                            <input
                              type="file"
                              multiple
                              aria-label="사진 추가"
                              accept={SUPPORTED_LISTING_IMAGE_TYPES.join(',')}
                              onChange={handleImageSelection}
                            />
                            <span aria-hidden="true">＋</span>
                            <small>사진 추가</small>
                          </label>
                        )}
                      </div>
                      <p className={styles.fieldHint}>
                        {images.length}/{MAX_LISTING_IMAGE_COUNT}장 ·{' '}
                        {formatMegabytes(totalImageBytes)} /{' '}
                        {formatMegabytes(MAX_LISTING_IMAGE_TOTAL_BYTES)}
                      </p>
                      {errors.images && (
                        <p className={styles.fieldError} role="alert">
                          {errors.images}
                        </p>
                      )}
                    </>
                  )}
                </fieldset>

                <label className={styles.formField}>
                  <span>물품명</span>
                  <input
                    value={draft.title}
                    maxLength={100}
                    placeholder="예: 3인 패브릭 소파"
                    onChange={(event) => updateDraft('title', event.target.value)}
                    aria-invalid={Boolean(errors.title)}
                  />
                  {errors.title && <small role="alert">{errors.title}</small>}
                </label>

                <label className={styles.formField}>
                  <span>가격</span>
                  <div className={styles.priceInput}>
                    <input
                      inputMode="numeric"
                      aria-label="가격"
                      value={priceInputValue(draft.price)}
                      placeholder="예: 30000"
                      onChange={(event) =>
                        updateDraft('price', parsePriceInput(event.target.value))
                      }
                      aria-invalid={Boolean(errors.price)}
                    />
                    <span aria-hidden="true">원</span>
                  </div>
                  {errors.price && <small role="alert">{errors.price}</small>}
                </label>

                <label className={styles.formField}>
                  <span>
                    상세 설명 <small>{draft.description.length} / 500</small>
                  </span>
                  <textarea
                    value={draft.description}
                    maxLength={500}
                    placeholder="상태와 사용 기간, 당근 링크 등을 적어 주세요."
                    onChange={(event) => updateDraft('description', event.target.value)}
                    aria-invalid={Boolean(errors.description)}
                  />
                  {errors.description && <small role="alert">{errors.description}</small>}
                </label>

                <label className={styles.formField}>
                  <span>
                    픽업 가능 시간 <small>{draft.pickupTimeText.length} / 50</small>
                  </span>
                  <input
                    value={draft.pickupTimeText}
                    maxLength={50}
                    placeholder="예: 평일 저녁 7시 이후"
                    onChange={(event) =>
                      updateDraft('pickupTimeText', event.target.value)
                    }
                    aria-invalid={Boolean(errors.pickupTimeText)}
                  />
                  {errors.pickupTimeText && (
                    <small role="alert">{errors.pickupTimeText}</small>
                  )}
                </label>

                <label className={styles.helpMoveField}>
                  <span>
                    <strong>같이 옮겨주기 가능</strong>
                    <small>가구를 옮길 때 도움을 줄 수 있나요?</small>
                  </span>
                  <input
                    type="checkbox"
                    role="switch"
                    checked={draft.canHelpMove}
                    onChange={(event) => updateDraft('canHelpMove', event.target.checked)}
                  />
                </label>

                <p className={styles.privacyNotice}>
                  연락처는 공개되지 않으며, 구매 문의는 익명 쪽지로 전달돼요.
                </p>

                {submitError && (
                  <p className={styles.inlineError} role="alert">
                    {submitError}
                  </p>
                )}

                <div className={styles.formActions}>
                  <button
                    type="button"
                    onClick={() => navigate('/mine')}
                    disabled={isSubmitting}
                  >
                    취소
                  </button>
                  <button type="submit" disabled={isSubmitting}>
                    {isSubmitting ? '저장 중…' : isEditing ? '수정하기' : '등록하기'}
                  </button>
                </div>
              </form>
            )}
          </section>
        </main>
      </MarketplaceShell>
    </AuthGate>
  );
}

export default ListingFormPage;
