import { useState } from 'react';
import type { FormEvent } from 'react';
import { createBuyerDispatchRequest } from '../api/dispatchApi';
import { DispatchApiError } from '../api/dispatchClient';
import FormField from '../components/FormField';
import HighValueToggle from '../components/HighValueToggle';
import MobileScreen from '../components/MobileScreen';
import NavBar from '../components/NavBar';
import PrimaryButton from '../components/PrimaryButton';
import PrivacyConsentField from '../components/PrivacyConsentField';
import { ErrorMessage } from '../components/StatusMessage';
import type { BuyerDispatchRequestCreateResponse } from '../model/dispatchTypes';
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

/**
 * 시안 `거래 링크 만들기` — 구매자가 실제 거래 정보를 입력해 배차 요청을 만든다.
 * 시안의 `받을 시간`은 server 계약에 대응 필드가 없어 렌더링하지 않는다.
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

  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [privacyConsent, setPrivacyConsent] = useState(false);
  const [privacyConsentError, setPrivacyConsentError] = useState('');

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

    try {
      const result = await createBuyerDispatchRequest({
        buyerName: buyerName.trim(),
        buyerPhoneNumber: buyerPhoneNumber.trim(),
        deliveryAddress: deliveryAddress.trim(),
        itemType: itemType.trim(),
        highValueItem,
        productLink: productLink.trim(),
      });
      onCreated(result);
    } catch (error) {
      setSubmitError(
        error instanceof DispatchApiError
          ? error.message
          : '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
      );
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
              {submitting ? '만드는 중이에요' : '링크 생성하기'}
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
