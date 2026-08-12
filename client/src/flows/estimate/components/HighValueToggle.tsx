import checkCircle from '@/flows/estimate/assets/check-circle.svg';
import styles from './HighValueToggle.module.css';

interface HighValueToggleProps {
  /** server `highValueItem` — 50만 원 초과 여부 */
  checked: boolean;
  onChange: (checked: boolean) => void;
}

const LABEL = '거래 금액';
const TEXT = '50만원 이상 거래예요';

/** 시안의 `거래 금액` 체크 항목. server 계약의 `highValueItem`과 1:1로 대응한다. */
export default function HighValueToggle({ checked, onChange }: HighValueToggleProps) {
  return (
    <div className={styles.field}>
      <span className={styles.label}>{LABEL}</span>
      <button
        className={styles.toggle}
        type="button"
        aria-pressed={checked}
        onClick={() => onChange(!checked)}
      >
        <span>{TEXT}</span>
        <span className={styles.check}>
          {checked ? <img className={styles.checkIcon} src={checkCircle} alt="" /> : null}
        </span>
      </button>
    </div>
  );
}
