import { useId } from 'react';
import checkCircle from '@/flows/estimate/assets/check-circle.svg';
import styles from './PrivacyConsentField.module.css';

interface PrivacyConsentFieldProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** 오른쪽 `보기`를 눌러 동의 안내 화면을 연다. */
  onOpenNotice: () => void;
  /** 동의하지 않고 제출했을 때의 안내 */
  error?: string;
}

export const PRIVACY_CONSENT_LABEL = '(필수) 개인정보 수집·이용 동의';

/** 시안의 제출 버튼 위 필수 동의 체크 항목 */
export default function PrivacyConsentField({
  checked,
  onChange,
  onOpenNotice,
  error,
}: PrivacyConsentFieldProps) {
  const errorId = useId();

  return (
    <div className={styles.field}>
      <div className={styles.row}>
        <label className={styles.consent}>
          <input
            className={styles.input}
            type="checkbox"
            checked={checked}
            aria-describedby={error ? errorId : undefined}
            onChange={(event) => onChange(event.target.checked)}
          />
          <span className={styles.box} aria-hidden="true">
            <img className={styles.checkIcon} src={checkCircle} alt="" />
          </span>
          <span className={styles.text}>{PRIVACY_CONSENT_LABEL}</span>
        </label>
        <button className={styles.noticeLink} type="button" onClick={onOpenNotice}>
          보기
        </button>
      </div>
      {error ? (
        <p className={styles.error} id={errorId} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
