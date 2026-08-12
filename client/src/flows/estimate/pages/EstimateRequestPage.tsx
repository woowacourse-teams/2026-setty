import { FormEvent, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '@/shared/api/http';
import { createEstimateRequest } from '@/flows/estimate/api/estimateRequestApi';
import FormField from '@/flows/estimate/components/FormField';
import HighValueToggle from '@/flows/estimate/components/HighValueToggle';
import MobileScreen from '@/flows/estimate/components/MobileScreen';
import NavBar from '@/flows/estimate/components/NavBar';
import PrimaryButton from '@/flows/estimate/components/PrimaryButton';
import PrivacyConsentField from '@/flows/estimate/components/PrivacyConsentField';
import { ESTIMATE_PRIVACY_POLICY_VERSION } from '@/flows/estimate/privacy/estimatePrivacyPolicy';
import PrivacyConsentNoticeScreen from '@/flows/estimate/privacy/PrivacyConsentNoticeScreen';
import {
  EstimateRequestField,
  EstimateRequestFieldErrors,
  EstimateRequestFormValues,
  MAX_PRODUCT_LINK,
  normalizePhoneNumber,
  validateEstimateRequest,
} from '@/flows/estimate/validation/estimateRequestValidation';
import styles from './EstimateRequestPage.module.css';

const INITIAL_VALUES: EstimateRequestFormValues = {
  name: '',
  phoneNumber: '',
  tradeArea: '',
  itemType: '',
  highValueItem: false,
  productLink: '',
};

const FIELD_NAMES: EstimateRequestField[] = [
  'name',
  'phoneNumber',
  'tradeArea',
  'itemType',
  'highValueItem',
  'productLink',
];

type EstimateRequestTextField = Exclude<EstimateRequestField, 'highValueItem'>;

function pickFieldErrors(
  fieldErrors?: Record<string, string>,
): EstimateRequestFieldErrors {
  if (!fieldErrors) {
    return {};
  }

  return FIELD_NAMES.reduce<EstimateRequestFieldErrors>((picked, fieldName) => {
    const message = fieldErrors[fieldName];
    if (message) {
      picked[fieldName] = message;
    }
    return picked;
  }, {});
}

const PRIVACY_CONSENT_ERROR = '개인정보 수집·이용에 동의해 주세요.';

/**
 * 동의 안내 화면은 같은 경로에 history 항목만 더해 연다.
 * 별도 경로로 이동하면 폼이 언마운트돼 입력값이 사라지고,
 * 상태로만 열면 히스토리가 쌓이지 않아 브라우저 뒤로가기가 사이트 밖으로 나간다.
 */
interface EstimateRequestLocationState {
  privacyNotice?: boolean;
}

export default function EstimateRequestPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState(INITIAL_VALUES);
  const [fieldErrors, setFieldErrors] = useState<EstimateRequestFieldErrors>({});
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [privacyConsent, setPrivacyConsent] = useState(false);
  const [privacyConsentError, setPrivacyConsentError] = useState('');

  const isPrivacyNoticeOpen =
    (location.state as EstimateRequestLocationState | null)?.privacyNotice === true;

  const openPrivacyNotice = () => {
    navigate(location.pathname, {
      state: { privacyNotice: true } satisfies EstimateRequestLocationState,
    });
  };

  const closePrivacyNotice = () => {
    navigate(-1);
  };

  const updatePrivacyConsent = (checked: boolean) => {
    setPrivacyConsent(checked);
    setPrivacyConsentError('');
  };

  const agreePrivacyConsent = () => {
    updatePrivacyConsent(true);
    closePrivacyNotice();
  };

  /** 문자 링크로 바로 들어오면 되돌아갈 이력이 없어 홈으로 보낸다. */
  const handleBack = () => {
    const historyIndex = (window.history.state as { idx?: number } | null)?.idx;
    if (typeof historyIndex === 'number' && historyIndex > 0) {
      navigate(-1);
      return;
    }
    navigate('/');
  };

  const updateValue = (field: EstimateRequestTextField, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setFormError('');
  };

  const updateHighValueItem = (checked: boolean) => {
    setValues((current) => ({ ...current, highValueItem: checked }));
    setFormError('');
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isSubmitting) {
      return;
    }

    const errors = validateEstimateRequest(values);
    setFieldErrors(errors);
    setPrivacyConsentError(privacyConsent ? '' : PRIVACY_CONSENT_ERROR);
    if (Object.keys(errors).length > 0 || !privacyConsent) {
      setFormError('');
      return;
    }

    setIsSubmitting(true);
    setFormError('');

    const productLink = values.productLink.trim();

    try {
      await createEstimateRequest({
        name: values.name.trim(),
        phoneNumber: normalizePhoneNumber(values.phoneNumber),
        tradeArea: values.tradeArea.trim(),
        itemType: values.itemType.trim(),
        highValueItem: values.highValueItem,
        ...(productLink ? { productLink } : {}),
        privacyConsent,
        privacyPolicyVersion: ESTIMATE_PRIVACY_POLICY_VERSION,
      });
      navigate('/estimate/submitted', { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        const serverFieldErrors = pickFieldErrors(error.fieldErrors);
        if (Object.keys(serverFieldErrors).length > 0) {
          setFieldErrors(serverFieldErrors);
        } else {
          setFormError('입력값을 다시 확인해 주세요.');
        }
      } else {
        setFormError('요청을 접수하지 못했어요. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isPrivacyNoticeOpen) {
    return (
      <PrivacyConsentNoticeScreen
        onBack={closePrivacyNotice}
        onAgree={agreePrivacyConsent}
      />
    );
  }

  return (
    <form className={styles.form} noValidate onSubmit={handleSubmit}>
      <MobileScreen
        header={<NavBar title="예상 견적 확인" onBack={handleBack} />}
        footer={
          <div className={styles.footer}>
            <PrivacyConsentField
              checked={privacyConsent}
              error={privacyConsentError}
              onChange={updatePrivacyConsent}
              onOpenNotice={openPrivacyNotice}
            />
            {formError ? (
              <p className={styles.formError} role="alert">
                {formError}
              </p>
            ) : null}
            <PrimaryButton type="submit" disabled={isSubmitting}>
              {isSubmitting ? '접수하고 있어요…' : '예상 견적 요청하기'}
            </PrimaryButton>
          </div>
        }
      >
        <div className={styles.fields}>
          <FormField
            label="상품명"
            placeholder="원목 의자"
            maxLength={100}
            value={values.itemType}
            error={fieldErrors.itemType}
            onChange={(event) => updateValue('itemType', event.target.value)}
          />
          <FormField
            label="당근 게시물 링크"
            inputMode="url"
            autoComplete="off"
            placeholder="https://www.daangn.com/articles/..."
            maxLength={MAX_PRODUCT_LINK}
            hint="입력하지 않아도 돼요. 넣어 주시면 물품을 더 정확히 확인해요"
            value={values.productLink}
            error={fieldErrors.productLink}
            onChange={(event) => updateValue('productLink', event.target.value)}
          />
          <HighValueToggle
            checked={values.highValueItem}
            onChange={updateHighValueItem}
          />
          <FormField
            label="거래 지역"
            placeholder="서울 강남구"
            maxLength={100}
            hint="동·상세주소는 입력하지 않아도 돼요"
            value={values.tradeArea}
            error={fieldErrors.tradeArea}
            onChange={(event) => updateValue('tradeArea', event.target.value)}
          />
          <FormField
            label="이름"
            placeholder="홍길동"
            autoComplete="name"
            maxLength={10}
            value={values.name}
            error={fieldErrors.name}
            onChange={(event) => updateValue('name', event.target.value)}
          />
          <FormField
            label="연락처"
            type="tel"
            inputMode="tel"
            placeholder="01012345678"
            autoComplete="tel"
            hint="예상 금액을 문자로 보내드려요"
            value={values.phoneNumber}
            error={fieldErrors.phoneNumber}
            onChange={(event) => updateValue('phoneNumber', event.target.value)}
          />
        </div>
      </MobileScreen>
    </form>
  );
}
