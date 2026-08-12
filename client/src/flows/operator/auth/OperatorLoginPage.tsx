import { FormEvent, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '@/shared/api/http';
import { validateOperatorSecret } from './operatorAuthApi';
import { storeOperatorSecret } from './operatorSecretStorage';
import styles from './OperatorAuth.module.css';

interface LoginLocationState {
  from?: string;
}

export default function OperatorLoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!password) {
      setErrorMessage('운영자 비밀번호를 입력해 주세요.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage('');

    try {
      await validateOperatorSecret(password);
      storeOperatorSecret(password);
      const from = (location.state as LoginLocationState | null)?.from;
      navigate(from || '/operator', { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setErrorMessage('비밀번호가 올바르지 않습니다.');
      } else if (error instanceof ApiError && error.status === 429) {
        setErrorMessage('로그인 시도가 많습니다. 잠시 후 다시 시도해 주세요.');
      } else {
        setErrorMessage('로그인하지 못했습니다. 서버 연결을 확인해 주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className={styles.loginPage}>
      <section className={styles.loginCard} aria-labelledby="operator-login-title">
        <p className={styles.brand}>SETTY OPERATOR</p>
        <h1 id="operator-login-title">운영자 로그인</h1>
        <p className={styles.description}>
          계정 없이 팀이 공유하는 운영자 비밀번호로 접속합니다.
        </p>

        <form className={styles.loginForm} noValidate onSubmit={handleSubmit}>
          <label>
            <span>비밀번호</span>
            <input
              aria-describedby={errorMessage ? 'operator-password-error' : undefined}
              aria-invalid={Boolean(errorMessage)}
              autoComplete="current-password"
              maxLength={200}
              name="password"
              required
              type="password"
              value={password}
              onChange={(event) => {
                setPassword(event.target.value);
                setErrorMessage('');
              }}
            />
          </label>

          {errorMessage && (
            <div
              className={styles.errorMessage}
              id="operator-password-error"
              role="alert"
            >
              {errorMessage}
            </div>
          )}

          <button disabled={isSubmitting} type="submit">
            {isSubmitting ? '확인하고 있어요…' : '운영자 페이지 들어가기'}
          </button>
        </form>

        <p className={styles.securityNote}>
          비밀번호는 현재 탭의 세션 저장소에만 보관하며 탭을 닫으면 삭제됩니다.
        </p>
      </section>
    </main>
  );
}
