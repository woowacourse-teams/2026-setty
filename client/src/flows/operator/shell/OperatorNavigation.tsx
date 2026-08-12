import { NavLink } from 'react-router-dom';
import styles from './OperatorNavigation.module.css';

const navigationItems = [
  { label: '견적 요청', to: '/operator/estimate-requests' },
  { label: '배차 요청', to: '/operator/dispatch-requests' },
];

export default function OperatorNavigation() {
  return (
    <nav className={styles.navigation} aria-label="운영자 메뉴">
      <div className={styles.navigationInner}>
        {navigationItems.map((item) => (
          <NavLink
            key={item.to}
            className={({ isActive }) =>
              `${styles.navigationLink} ${isActive ? styles.active : ''}`
            }
            to={item.to}
          >
            {item.label}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
