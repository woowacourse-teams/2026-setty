import logoCheck from '../assets/logo-check.svg';
import styles from './BrandHeader.module.css';

/** 시안의 상단 SETTY 로고 영역 */
export default function BrandHeader() {
  return (
    <header className={styles.header}>
      <span className={styles.mark}>
        <img className={styles.markIcon} src={logoCheck} alt="" />
      </span>
      <span className={styles.name}>SETTY</span>
    </header>
  );
}
