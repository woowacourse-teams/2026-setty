import type { ReactNode } from 'react';
import styles from './MobileScreen.module.css';

interface MobileScreenProps {
  /** 화면 상단 영역. `BrandHeader` 또는 `NavBar`를 넣는다. */
  header?: ReactNode;
  /** 스크롤되는 본문 */
  children: ReactNode;
  /** 하단에 고정되는 action 영역 */
  footer?: ReactNode;
}

export default function MobileScreen({ header, children, footer }: MobileScreenProps) {
  return (
    <div className={styles.screen}>
      {header}
      <div className={styles.body}>{children}</div>
      {footer ? <div className={styles.footer}>{footer}</div> : null}
    </div>
  );
}
