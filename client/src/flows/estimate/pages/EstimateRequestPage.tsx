import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '@/shared/api/http';
import { createEstimateRequest } from '@/flows/estimate/api/estimateRequestApi';
import FormField from '@/flows/estimate/components/FormField';
import HighValueToggle from '@/flows/estimate/components/HighValueToggle';
import MobileScreen from '@/flows/estimate/components/MobileScreen';
import NavBar from '@/flows/estimate/components/NavBar';
import PrimaryButton from '@/flows/estimate/components/PrimaryButton';
import {
  EstimateRequestField,
  EstimateRequestFieldErrors,
  EstimateRequestFormValues,
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
};

const FIELD_NAMES: EstimateRequestField[] = [
  'name',
  'phoneNumber',
  'tradeArea',
  'itemType',
  'highValueItem',
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

export default function EstimateRequestPage() {
  const navigate = useNavigate();
  const [values, setValues] = useState(INITIAL_VALUES);
  const [fieldErrors, setFieldErrors] = useState<EstimateRequestFieldErrors>({});
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

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
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setFormError('');
      return;
    }

    setIsSubmitting(true);
    setFormError('');

    try {
      await createEstimateRequest({
        name: values.name.trim(),
        phoneNumber: normalizePhoneNumber(values.phoneNumber),
        tradeArea: values.tradeArea.trim(),
        itemType: values.itemType.trim(),
        highValueItem: values.highValueItem,
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

  return (
    <form className={styles.form} noValidate onSubmit={handleSubmit}>
      <MobileScreen
        header={<NavBar title="예상 견적 확인" onBack={handleBack} />}
        footer={
          <div className={styles.footer}>
            {formError ? (
              <p className={styles.formError} role="alert">
                {formError}
              </p>
            ) : null}
            <PrimaryButton type="submit" disabled={isSubmitting}>
              {isSubmitting ? '접수하고 있어요…' : '예상 견적 요청하기'}
            </PrimaryButton>
            <p className={styles.operationNote}>
              운영시간 10:00~20:00, 운영시간 밖 요청은 다음 운영 시작 후 확인해요.
            </p>
          </div>
        }
      >
        <div className={styles.fields}>
          <FormField
            label="상품명"
            placeholder="예: 원목 의자"
            maxLength={100}
            value={values.itemType}
            error={fieldErrors.itemType}
            onChange={(event) => updateValue('itemType', event.target.value)}
          />
          <HighValueToggle
            checked={values.highValueItem}
            onChange={updateHighValueItem}
          />
          <FormField
            label="거래 지역"
            placeholder="예: 서울 강남구"
            maxLength={100}
            hint="동·상세주소는 입력하지 않아도 돼요"
            value={values.tradeArea}
            error={fieldErrors.tradeArea}
            onChange={(event) => updateValue('tradeArea', event.target.value)}
          />
          <FormField
            label="이름"
            placeholder="예: 홍길동"
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
            placeholder="010-0000-0000"
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
