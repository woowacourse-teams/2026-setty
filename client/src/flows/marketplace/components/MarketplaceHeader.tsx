import { NavLink } from 'react-router-dom';
import MarketplaceIcon from './MarketplaceIcon';
import styles from './MarketplaceHeader.module.css';

interface HeaderLinkProps {
  label: string;
  to: string;
  icon: 'inbox' | 'mine';
}

function HeaderLink({ label, to, icon }: HeaderLinkProps) {
  return (
    <NavLink
      aria-label={label}
      className={({ isActive }) => `${styles.navLink} ${isActive ? styles.active : ''}`}
      to={to}
    >
      <MarketplaceIcon className={styles.navIcon} name={icon} />
    </NavLink>
  );
}

export default function MarketplaceHeader() {
  return (
    <header className={styles.header}>
      <NavLink aria-label="세티 홈" className={styles.brand} end to="/">
        <span aria-hidden="true" className={styles.brandMark} />
        <span className={styles.brandText}>세티</span>
      </NavLink>

      <nav aria-label="주요 메뉴" className={styles.navigation}>
        <HeaderLink icon="mine" label="내 매물" to="/mine" />
        <HeaderLink icon="inbox" label="쪽지함" to="/inbox" />
      </nav>
    </header>
  );
}
