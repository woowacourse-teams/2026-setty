import { Link, Outlet, useNavigate } from 'react-router-dom';
import { clearOperatorSecret } from '@/flows/operator/auth/operatorSecretStorage';
import OperatorNavigation from './OperatorNavigation';
import styles from './OperatorShell.module.css';

export default function OperatorShell() {
  const navigate = useNavigate();

  const handleLogout = () => {
    clearOperatorSecret();
    navigate('/operator/login', { replace: true });
  };

  return (
    <div className={styles.appShell}>
      <header className={styles.header}>
        <div>
          <Link className={styles.brand} to="/operator">
            SETTY
          </Link>
          <span className={styles.role}>운영자</span>
        </div>
        <div className={styles.actions}>
          <button type="button" onClick={handleLogout}>
            로그아웃
          </button>
        </div>
      </header>
      <OperatorNavigation />
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
}
