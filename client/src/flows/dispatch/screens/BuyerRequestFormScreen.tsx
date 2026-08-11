import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { createBuyerDispatchRequest, uploadDispatchItemImage } from '../api/dispatchApi';
import { DispatchApiError } from '../api/dispatchClient';
import FormField from '../components/FormField';
import HighValueToggle from '../components/HighValueToggle';
import ItemImageField from '../components/ItemImageField';
import MobileScreen from '../components/MobileScreen';
import NavBar from '../components/NavBar';
import PrimaryButton from '../components/PrimaryButton';
import PrivacyConsentField from '../components/PrivacyConsentField';
import { ErrorMessage } from '../components/StatusMessage';
import type { BuyerDispatchRequestCreateResponse } from '../model/dispatchTypes';
import {
  ITEM_IMAGE_TYPE_ERROR,
  releaseItemImagePreview,
  type SelectedItemImage,
} from '../model/itemImage';
import PrivacyConsentNoticeScreen from './PrivacyConsentNoticeScreen';
import styles from './BuyerRequestFormScreen.module.css';

interface BuyerRequestFormScreenProps {
  onBack: () => void;
  onCreated: (result: BuyerDispatchRequestCreateResponse) => void;
  /** 동의 안내 화면이 열려 있는지. 화면 전환은 URL이 결정한다. */
  isPrivacyNoticeOpen: boolean;
  onOpenPrivacyNotice: () => void;
  onClosePrivacyNotice: () => void;
}

const PRIVACY_CONSENT_ERROR = '개인정보 수집·이용에 동의해 주세요.';

/** server `BuyerDispatchRequestCreateRequest`의 `@Size` 제약과 같은 값이다. */
const MAX_BUYER_NAME = 50;
const MAX_ITEM_TYPE = 100;
const MAX_DELIVERY_ADDRESS = 255;
const MAX_PRODUCT_LINK = 500;

/** server `@Pattern(regexp = "^01\\d-?\\d{3,4}-?\\d{4}$")`와 같은 식이다. */
const PHONE_NUMBER_PATTERN = /^01\d-?\d{3,4}-?\d{4}$/;

interface FieldErrors {
  itemType?: string;
  buyerName?: string;
  buyerPhoneNumber?: string;
  deliveryAddress?: string;
  productLink?: string;
}

const validate = (values: {
  itemType: string;
  buyerName: string;
  buyerPhoneNumber: string;
  deliveryAddress: string;
  productLink: string;
}): FieldErrors => {
  const errors: FieldErrors = {};

  if (!values.itemType.trim()) {
    errors.itemType = '상품명을 입력해 주세요.';
  } else if (values.itemType.trim().length > MAX_ITEM_TYPE) {
    errors.itemType = `상품명은 ${MAX_ITEM_TYPE}자까지 입력할 수 있어요.`;
  }

  if (!values.buyerName.trim()) {
    errors.buyerName = '이름을 입력해 주세요.';
  } else if (values.buyerName.trim().length > MAX_BUYER_NAME) {
    errors.buyerName = `이름은 ${MAX_BUYER_NAME}자까지 입력할 수 있어요.`;
  }

  if (!values.buyerPhoneNumber.trim()) {
    errors.buyerPhoneNumber = '연락처를 입력해 주세요.';
  } else if (!PHONE_NUMBER_PATTERN.test(values.buyerPhoneNumber.trim())) {
    errors.buyerPhoneNumber = '01012345678 형식으로 입력해 주세요.';
  }

  if (!values.deliveryAddress.trim()) {
    errors.deliveryAddress = '받는 주소를 입력해 주세요.';
  } else if (values.deliveryAddress.trim().length > MAX_DELIVERY_ADDRESS) {
    errors.deliveryAddress = `주소는 ${MAX_DELIVERY_ADDRESS}자까지 입력할 수 있어요.`;
  }

  if (!values.productLink.trim()) {
    errors.productLink = '당근 게시물 링크를 입력해 주세요.';
  } else if (values.productLink.trim().length > MAX_PRODUCT_LINK) {
    errors.productLink = `링크는 ${MAX_PRODUCT_LINK}자까지 입력할 수 있어요.`;
  }

  return errors;
};

const FALLBACK_ERROR_MESSAGE = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';

const toMessage = (error: unknown): string =>
  error instanceof DispatchApiError ? error.message : FALLBACK_ERROR_MESSAGE;

/** 사진 업로드와 요청 생성은 순서대로 일어나므로 진행 중인 단계를 그대로 알린다. */
const submitLabelOf = (uploading: boolean, submitting: boolean): string => {
  if (uploading) {
    return '사진을 올리는 중이에요';
  }
  if (submitting) {
    return '만드는 중이에요';
  }

  return '링크 생성하기';
};

/**
 * 시안 `거래 링크 만들기` — 구매자가 실제 거래 정보를 입력해 배차 요청을 만든다.
 * 시안의 `받을 시간`은 server 계약에 대응 필드가 없어 렌더링하지 않는다.
 *
 * `물품 상태 사진`은 server `itemImageUrls`가 null을 허용하므로 선택 항목으로 둔다.
 * DEC-016·DEC-023은 필수로 적었으나 계약과 갈려 Issue #90에서 확정 전이며,
 * 여기에서 필수 검증을 임의로 만들지 않는다.
 */
export default function BuyerRequestFormScreen({
  onBack,
  onCreated,
  isPrivacyNoticeOpen,
  onOpenPrivacyNotice,
  onClosePrivacyNotice,
}: BuyerRequestFormScreenProps) {
  const [itemType, setItemType] = useState('');
  const [highValueItem, setHighValueItem] = useState(false);
  const [buyerName, setBuyerName] = useState('');
  const [buyerPhoneNumber, setBuyerPhoneNumber] = useState('');
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [productLink, setProductLink] = useState('');

  /** 선택 항목인 물품 상태 사진. 파일과 미리보기 URL을 함께 들고 같이 버린다. */
  const [itemImage, setItemImage] = useState<SelectedItemImage | null>(null);
  const [itemImageError, setItemImageError] = useState('');
  /**
   * 업로드에 성공한 사진의 URL이다.
   * 배차 생성이 실패해 다시 제출할 때 같은 파일을 두 번 올리지 않는다.
   */
  const [uploadedImageUrl, setUploadedImageUrl] = useState<string | null>(null);
  /** 언마운트 정리에서 최신 미리보기 URL을 읽기 위한 참조 */
  const itemImagePreviewUrlRef = useRef<string | null>(null);

  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [privacyConsent, setPrivacyConsent] = useState(false);
  const [privacyConsentError, setPrivacyConsentError] = useState('');

  /** 화면을 떠날 때 남은 미리보기 URL을 해제한다. */
  useEffect(
    () => () => {
      releaseItemImagePreview(itemImagePreviewUrlRef);
    },
    [],
  );

  const selectItemImage = (file: File) => {
    releaseItemImagePreview(itemImagePreviewUrlRef);
    // 사진이 바뀌면 앞서 올린 URL은 더 이상 이 첨부의 것이 아니다.
    setUploadedImageUrl(null);

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
    setUploadedImageUrl(null);
  };

  const updatePrivacyConsent = (checked: boolean) => {
    setPrivacyConsent(checked);
    setPrivacyConsentError('');
  };

  const agreePrivacyConsent = () => {
    updatePrivacyConsent(true);
    onClosePrivacyNotice();
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) {
      return;
    }

    const errors = validate({
      itemType,
      buyerName,
      buyerPhoneNumber,
      deliveryAddress,
      productLink,
    });
    setFieldErrors(errors);
    setPrivacyConsentError(privacyConsent ? '' : PRIVACY_CONSENT_ERROR);
    if (Object.keys(errors).length > 0 || !privacyConsent) {
      setSubmitError('');
      return;
    }

    setSubmitting(true);
    setSubmitError('');

    /*
     * 사진은 배차 요청 본문에 URL로만 들어가므로 생성보다 먼저 올린다.
     * 업로드가 실패하면 사진 없는 요청을 대신 만들지 않고 여기에서 멈춘다.
     */
    let imageUrl = uploadedImageUrl;
    if (itemImage && !imageUrl) {
      setUploading(true);
      try {
        imageUrl = (await uploadDispatchItemImage(itemImage.file)).imageUrl;
        setUploadedImageUrl(imageUrl);
      } catch (error) {
        setItemImageError(toMessage(error));
        setSubmitting(false);
        setUploading(false);
        return;
      }
      setUploading(false);
    }

    try {
      const result = await createBuyerDispatchRequest({
        buyerName: buyerName.trim(),
        buyerPhoneNumber: buyerPhoneNumber.trim(),
        deliveryAddress: deliveryAddress.trim(),
        itemType: itemType.trim(),
        highValueItem,
        productLink: productLink.trim(),
        ...(imageUrl ? { itemImageUrls: [imageUrl] } : {}),
      });
      onCreated(result);
    } catch (error) {
      setSubmitError(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  if (isPrivacyNoticeOpen) {
    return (
      <PrivacyConsentNoticeScreen
        onBack={onClosePrivacyNotice}
        onAgree={agreePrivacyConsent}
      />
    );
  }

  const submitLabel = submitLabelOf(uploading, submitting);

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <MobileScreen
        header={<NavBar title="거래 링크 만들기" onBack={onBack} />}
        footer={
          <div className={styles.footer}>
            <PrivacyConsentField
              checked={privacyConsent}
              error={privacyConsentError}
              onChange={updatePrivacyConsent}
              onOpenNotice={onOpenPrivacyNotice}
            />
            {submitError ? <ErrorMessage message={submitError} /> : null}
            <PrimaryButton type="submit" disabled={submitting}>
              {submitLabel}
            </PrimaryButton>
          </div>
        }
      >
        <div className={styles.fields}>
          <FormField
            label="상품명"
            placeholder="예: 3인용 소파"
            value={itemType}
            maxLength={MAX_ITEM_TYPE}
            error={fieldErrors.itemType}
            onChange={(event) => setItemType(event.target.value)}
          />
          <FormField
            label="당근 게시물 링크"
            inputMode="url"
            autoComplete="off"
            placeholder="https://www.daangn.com/articles/..."
            value={productLink}
            maxLength={MAX_PRODUCT_LINK}
            error={fieldErrors.productLink}
            hint="거래 중인 당근 게시물 링크를 붙여넣어 주세요"
            onChange={(event) => setProductLink(event.target.value)}
          />
          <ItemImageField
            previewUrl={itemImage?.previewUrl ?? null}
            error={itemImageError}
            onSelect={selectItemImage}
            onRemove={removeItemImage}
          />
          <HighValueToggle checked={highValueItem} onChange={setHighValueItem} />
          <FormField
            label="구매자 이름"
            placeholder="이름을 입력해 주세요"
            autoComplete="name"
            value={buyerName}
            maxLength={MAX_BUYER_NAME}
            error={fieldErrors.buyerName}
            onChange={(event) => setBuyerName(event.target.value)}
          />
          <FormField
            label="연락처"
            type="tel"
            inputMode="numeric"
            placeholder="01012345678"
            autoComplete="tel"
            value={buyerPhoneNumber}
            error={fieldErrors.buyerPhoneNumber}
            onChange={(event) => setBuyerPhoneNumber(event.target.value)}
          />
          <FormField
            label="받는 주소"
            placeholder="주소를 입력해 주세요"
            value={deliveryAddress}
            maxLength={MAX_DELIVERY_ADDRESS}
            error={fieldErrors.deliveryAddress}
            onChange={(event) => setDeliveryAddress(event.target.value)}
          />
        </div>
      </MobileScreen>
    </form>
  );
}
