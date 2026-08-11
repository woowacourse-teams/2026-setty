import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { findSellerInputSession, submitSellerInput } from '../api/dispatchApi';
import { DispatchApiError } from '../api/dispatchClient';
import FormField from '../components/FormField';
import ItemImageField from '../components/ItemImageField';
import MobileScreen from '../components/MobileScreen';
import NavBar from '../components/NavBar';
import PrimaryButton from '../components/PrimaryButton';
import PrivacyConsentField from '../components/PrivacyConsentField';
import { ErrorMessage, LoadingMessage } from '../components/StatusMessage';
import TextButton from '../components/TextButton';
import type { SellerInputSessionResponse } from '../model/dispatchTypes';
import {
  ITEM_IMAGE_TYPE_ERROR,
  releaseItemImagePreview,
  type SelectedItemImage,
} from '../model/itemImage';
import PrivacyConsentNoticeScreen from './PrivacyConsentNoticeScreen';
import styles from './SellerInputFormScreen.module.css';

interface SellerInputFormScreenProps {
  /** 판매자 전용 링크의 세션 토큰 */
  sellerToken: string;
  onBack?: () => void;
  onSubmitted: () => void;
  /** 동의 안내 화면이 열려 있는지. 화면 전환은 URL이 결정한다. */
  isPrivacyNoticeOpen: boolean;
  onOpenPrivacyNotice: () => void;
  onClosePrivacyNotice: () => void;
}

const PRIVACY_CONSENT_ERROR = '개인정보 수집·이용에 동의해 주세요.';

/** server `SellerInputSubmitRequest`의 `@Size` 제약과 같은 값이다. */
const MAX_SELLER_NAME = 50;
const MAX_PICKUP_ADDRESS = 255;
const MAX_AVAILABLE_PICKUP_TIME = 100;

/** server `@Pattern(regexp = "^01\\d-?\\d{3,4}-?\\d{4}$")`와 같은 식이다. */
const PHONE_NUMBER_PATTERN = /^01\d-?\d{3,4}-?\d{4}$/;

const FALLBACK_ERROR_MESSAGE = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';

interface FieldErrors {
  sellerName?: string;
  sellerPhoneNumber?: string;
  pickupAddress?: string;
  availablePickupTime?: string;
}

interface FormValues {
  sellerName: string;
  sellerPhoneNumber: string;
  pickupAddress: string;
  availablePickupTime: string;
}

const validate = (values: FormValues): FieldErrors => {
  const errors: FieldErrors = {};

  if (!values.sellerName.trim()) {
    errors.sellerName = '이름을 입력해 주세요.';
  } else if (values.sellerName.trim().length > MAX_SELLER_NAME) {
    errors.sellerName = `이름은 ${MAX_SELLER_NAME}자까지 입력할 수 있어요.`;
  }

  if (!values.sellerPhoneNumber.trim()) {
    errors.sellerPhoneNumber = '연락처를 입력해 주세요.';
  } else if (!PHONE_NUMBER_PATTERN.test(values.sellerPhoneNumber.trim())) {
    errors.sellerPhoneNumber = '01012345678 형식으로 입력해 주세요.';
  }

  if (!values.pickupAddress.trim()) {
    errors.pickupAddress = '발송 주소를 입력해 주세요.';
  } else if (values.pickupAddress.trim().length > MAX_PICKUP_ADDRESS) {
    errors.pickupAddress = `주소는 ${MAX_PICKUP_ADDRESS}자까지 입력할 수 있어요.`;
  }

  if (!values.availablePickupTime.trim()) {
    errors.availablePickupTime = '회수 희망 시간을 입력해 주세요.';
  } else if (values.availablePickupTime.trim().length > MAX_AVAILABLE_PICKUP_TIME) {
    errors.availablePickupTime = `회수 희망 시간은 ${MAX_AVAILABLE_PICKUP_TIME}자까지 입력할 수 있어요.`;
  }

  return errors;
};

const toMessage = (error: unknown): string =>
  error instanceof DispatchApiError ? error.message : FALLBACK_ERROR_MESSAGE;

/**
 * 시안 `물품 정보 입력` — 판매자가 전용 링크로 들어와 발송 정보를 입력한다.
 *
 * 구매자의 이름·연락처·상세주소는 DEC-017에 따라 판매자에게 보여주지 않는다.
 * 세션 응답에도 없으므로 화면에서 추측해 만들지 않는다.
 * 시안의 `거래 금액`은 세션 응답에 대응 필드가 없어 렌더링하지 않는다.
 * `물품 상태 사진`은 선택 항목이며, 판매자 세션용 업로드 계약이 아직 없어
 * 첨부·미리보기까지만 하고 제출 payload에 넣지 않는다.
 */
export default function SellerInputFormScreen({
  sellerToken,
  onBack,
  onSubmitted,
  isPrivacyNoticeOpen,
  onOpenPrivacyNotice,
  onClosePrivacyNotice,
}: SellerInputFormScreenProps) {
  const [session, setSession] = useState<SellerInputSessionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  /** 다시 시도 버튼이 세션 조회 effect를 다시 실행하게 하는 값 */
  const [reloadCount, setReloadCount] = useState(0);

  const [sellerName, setSellerName] = useState('');
  const [sellerPhoneNumber, setSellerPhoneNumber] = useState('');
  const [pickupAddress, setPickupAddress] = useState('');
  const [availablePickupTime, setAvailablePickupTime] = useState('');

  /** 선택 항목인 물품 상태 사진. 파일과 미리보기 URL을 함께 들고 같이 버린다. */
  const [itemImage, setItemImage] = useState<SelectedItemImage | null>(null);
  const [itemImageError, setItemImageError] = useState('');
  /** 언마운트 정리에서 최신 미리보기 URL을 읽기 위한 참조 */
  const itemImagePreviewUrlRef = useRef<string | null>(null);

  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [privacyConsent, setPrivacyConsent] = useState(false);
  const [privacyConsentError, setPrivacyConsentError] = useState('');

  useEffect(() => {
    // StrictMode의 이중 마운트에서 먼저 끝난 응답이 늦게 덮어쓰지 않도록 정리한다.
    let active = true;

    findSellerInputSession(sellerToken)
      .then((response) => {
        if (!active) {
          return;
        }
        setSession(response);
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }
        setSession(null);
        setLoadError(toMessage(error));
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
  }, [sellerToken, reloadCount]);

  /** 화면을 떠날 때 남은 미리보기 URL을 해제한다. */
  useEffect(
    () => () => {
      releaseItemImagePreview(itemImagePreviewUrlRef);
    },
    [],
  );

  const selectItemImage = (file: File) => {
    releaseItemImagePreview(itemImagePreviewUrlRef);

    // accept 속성은 파일 선택창의 필터일 뿐이라 고른 파일을 다시 확인한다.
    if (!file.type.startsWith('image/')) {
      setItemImage(null);
      setItemImageError(ITEM_IMAGE_TYPE_ERROR);
      return;
    }

    const previewUrl = URL.createObjectURL(file);
    itemImagePreviewUrlRef.current = previewUrl;
    setItemImage({ file, previewUrl });
    setItemImageError('');
  };

  const removeItemImage = () => {
    releaseItemImagePreview(itemImagePreviewUrlRef);
    setItemImage(null);
    setItemImageError('');
  };

  const updatePrivacyConsent = (checked: boolean) => {
    setPrivacyConsent(checked);
    setPrivacyConsentError('');
  };

  const agreePrivacyConsent = () => {
    updatePrivacyConsent(true);
    onClosePrivacyNotice();
  };

  /** 조회 상태 초기화는 effect 본문이 아니라 재시도 event에서 한다. */
  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError('');
    setReloadCount((count) => count + 1);
  }, []);

  const alreadySubmitted = session?.alreadySubmitted === true;
  const canSubmit = session !== null && !alreadySubmitted;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting || !canSubmit) {
      return;
    }

    const values: FormValues = {
      sellerName,
      sellerPhoneNumber,
      pickupAddress,
      availablePickupTime,
    };
    const errors = validate(values);
    setFieldErrors(errors);
    setPrivacyConsentError(privacyConsent ? '' : PRIVACY_CONSENT_ERROR);
    if (Object.keys(errors).length > 0 || !privacyConsent) {
      setSubmitError('');
      return;
    }

    setSubmitting(true);
    setSubmitError('');

    try {
      await submitSellerInput(sellerToken, {
        sellerName: sellerName.trim(),
        sellerPhoneNumber: sellerPhoneNumber.trim(),
        pickupAddress: pickupAddress.trim(),
        availablePickupTime: availablePickupTime.trim(),
      });
      onSubmitted();
    } catch (error) {
      // 409 중복 제출을 포함해 server 메시지를 그대로 보여주고 성공으로 넘기지 않는다.
      setSubmitError(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const header = <NavBar title="물품 정보 입력" onBack={onBack} />;

  if (isPrivacyNoticeOpen) {
    /*
     * 폼을 언마운트하면 입력값과 첨부한 사진이 사라지므로 같은 컴포넌트 안에서 화면만 바꾼다.
     * 열고 닫는 히스토리 관리는 route가 한다.
     */
    return (
      <PrivacyConsentNoticeScreen
        onBack={onClosePrivacyNotice}
        onAgree={agreePrivacyConsent}
      />
    );
  }

  if (loading) {
    return (
      <MobileScreen header={header}>
        <div className={styles.status}>
          <LoadingMessage message="요청 정보를 불러오는 중이에요" />
        </div>
      </MobileScreen>
    );
  }

  if (loadError) {
    return (
      <MobileScreen header={header}>
        <div className={styles.status}>
          <ErrorMessage message={loadError} />
          <TextButton onClick={retryLoad}>다시 시도</TextButton>
        </div>
      </MobileScreen>
    );
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <MobileScreen
        header={header}
        footer={
          <div className={styles.footer}>
            {alreadySubmitted ? null : (
              <PrivacyConsentField
                checked={privacyConsent}
                error={privacyConsentError}
                onChange={updatePrivacyConsent}
                onOpenNotice={onOpenPrivacyNotice}
              />
            )}
            {submitError ? <ErrorMessage message={submitError} /> : null}
            <PrimaryButton type="submit" disabled={submitting || !canSubmit}>
              {submitting ? '제출하는 중이에요' : '제출하기'}
            </PrimaryButton>
          </div>
        }
      >
        <div className={styles.content}>
          <dl className={styles.summary}>
            <div className={styles.summaryRow}>
              <dt className={styles.summaryLabel}>상품명</dt>
              <dd className={styles.summaryValue}>{session?.itemType ?? '-'}</dd>
            </div>
          </dl>

          {alreadySubmitted ? (
            <p className={styles.notice} role="status">
              이미 제출된 요청이에요
            </p>
          ) : (
            <div className={styles.fields}>
              <ItemImageField
                previewUrl={itemImage?.previewUrl ?? null}
                error={itemImageError}
                onSelect={selectItemImage}
                onRemove={removeItemImage}
              />
              <FormField
                label="판매자 이름"
                placeholder="이름을 입력해 주세요"
                autoComplete="name"
                value={sellerName}
                maxLength={MAX_SELLER_NAME}
                error={fieldErrors.sellerName}
                onChange={(event) => setSellerName(event.target.value)}
              />
              <FormField
                label="연락처"
                type="tel"
                inputMode="numeric"
                placeholder="01012345678"
                autoComplete="tel"
                value={sellerPhoneNumber}
                error={fieldErrors.sellerPhoneNumber}
                onChange={(event) => setSellerPhoneNumber(event.target.value)}
              />
              <FormField
                label="발송 주소"
                placeholder="물품을 회수할 주소를 입력해 주세요"
                value={pickupAddress}
                maxLength={MAX_PICKUP_ADDRESS}
                error={fieldErrors.pickupAddress}
                onChange={(event) => setPickupAddress(event.target.value)}
              />
              <FormField
                label="회수 희망 시간"
                placeholder="예: 평일 오후 2시 이후"
                value={availablePickupTime}
                maxLength={MAX_AVAILABLE_PICKUP_TIME}
                error={fieldErrors.availablePickupTime}
                onChange={(event) => setAvailablePickupTime(event.target.value)}
              />
            </div>
          )}
        </div>
      </MobileScreen>
    </form>
  );
}
