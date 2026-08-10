import backArrow from '@/flows/estimate/assets/back-arrow.svg';
import styles from './NavBar.module.css';

interface NavBarProps {
  title: string;
  /** 없으면 뒤로가기 버튼을 렌더링하지 않는다. */
  onBack?: () => void;
}

/** 시안의 뒤로가기 + 화면 제목 상단 바 */
export default function NavBar({ title, onBack }: NavBarProps) {
  return (
    <header className={styles.bar}>
      {onBack ? (
        <button
          className={styles.back}
          type="button"
          onClick={onBack}
          aria-label="뒤로 가기"
        >
          <img className={styles.backIcon} src={backArrow} alt="" />
        </button>
      ) : null}
      <h1 className={styles.title}>{title}</h1>
    </header>
  );
}
