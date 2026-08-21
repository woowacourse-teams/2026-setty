import { FormEvent, useEffect, useRef, useState } from 'react';
import { loginOrCreateAccount } from '@/flows/marketplace/api/marketplaceApi';
import styles from './AuthModal.module.css';

const PHONE_NUMBER_PATTERN = /^\d{10,11}$/;
const PASSWORD_PATTERN = /^\d{4}$/;

interface AuthModalProps {
  open: boolean;
  onCancel: () => void;
  onAuthenticated: () => void | Promise<void>;
}

function getLoginErrorMessage(error: unknown) {
  if (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    error.status === 401
  ) {
    return '휴대폰 번호 또는 비밀번호를 다시 확인해 주세요.';
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return '로그인하지 못했어요. 잠시 후 다시 시도해 주세요.';
}

export function AuthModal({ open, onCancel, onAuthenticated }: AuthModalProps) {
  const phoneInputRef = useRef<HTMLInputElement>(null);
  const dialogRef = useRef<HTMLElement>(null);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;

    const previouslyFocused =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusFrame = window.requestAnimationFrame(() => phoneInputRef.current?.focus());

    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
  }, [open]);

  if (!open) return null;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrorMessage('');

    const normalizedPhoneNumber = phoneNumber.replace(/\D/g, '');
    if (!PHONE_NUMBER_PATTERN.test(normalizedPhoneNumber)) {
      setErrorMessage('휴대폰 번호는 숫자 10~11자리로 입력해 주세요.');
      return;
    }

    if (!PASSWORD_PATTERN.test(password)) {
      setErrorMessage('비밀번호는 숫자 4자리로 입력해 주세요.');
      return;
    }

    setIsSubmitting(true);
    try {
      await loginOrCreateAccount({
        phoneNumber: normalizedPhoneNumber,
        password,
      });
      setPassword('');
      setIsSubmitting(false);
      await onAuthenticated();
    } catch (error) {
      setErrorMessage(getLoginErrorMessage(error));
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className={styles.backdrop}
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isSubmitting) {
          setErrorMessage('');
          onCancel();
        }
      }}
      onKeyDown={(event) => {
        if (event.key === 'Escape' && !isSubmitting) {
          setErrorMessage('');
          onCancel();
          return;
        }

        if (event.key === 'Tab') {
          const focusableElements = Array.from(
            dialogRef.current?.querySelectorAll<HTMLElement>(
              'input:not([disabled]), button:not([disabled])',
            ) ?? [],
          );
          const firstElement = focusableElements[0];
          const lastElement = focusableElements.at(-1);
          if (!firstElement || !lastElement) {
            event.preventDefault();
            dialogRef.current?.focus();
            return;
          }

          if (event.shiftKey && document.activeElement === firstElement) {
            event.preventDefault();
            lastElement.focus();
          } else if (!event.shiftKey && document.activeElement === lastElement) {
            event.preventDefault();
            firstElement.focus();
          }
        }
      }}
    >
      <section
        ref={dialogRef}
        className={styles.dialog}
        role="dialog"
        tabIndex={-1}
        aria-modal="true"
        aria-labelledby="marketplace-auth-title"
        aria-describedby="marketplace-auth-description"
      >
        <h2 id="marketplace-auth-title">로그인이 필요해요</h2>
        <p id="marketplace-auth-description">
          휴대폰 번호와 숫자 4자리 비밀번호를 입력해 주세요. 처음 사용하는 번호라면 계정이
          함께 만들어져요.
        </p>

        <form onSubmit={(event) => void handleSubmit(event)} noValidate>
          <label>
            <span>휴대폰 번호</span>
            <input
              ref={phoneInputRef}
              type="tel"
              inputMode="numeric"
              autoComplete="tel"
              value={phoneNumber}
              placeholder="01012345678"
              maxLength={13}
              disabled={isSubmitting}
              onChange={(event) => {
                setPhoneNumber(event.target.value.replace(/[^\d-]/g, ''));
                setErrorMessage('');
              }}
            />
          </label>

          <label>
            <span>비밀번호</span>
            <input
              type="password"
              inputMode="numeric"
              autoComplete="current-password"
              value={password}
              placeholder="숫자 4자리"
              maxLength={4}
              disabled={isSubmitting}
              onChange={(event) => {
                setPassword(event.target.value.replace(/\D/g, '').slice(0, 4));
                setErrorMessage('');
              }}
            />
          </label>

          {errorMessage && (
            <p className={styles.error} role="alert">
              {errorMessage}
            </p>
          )}

          <div className={styles.actions}>
            <button
              className={styles.cancelButton}
              type="button"
              disabled={isSubmitting}
              onClick={() => {
                setErrorMessage('');
                onCancel();
              }}
            >
              취소
            </button>
            <button className={styles.submitButton} type="submit" disabled={isSubmitting}>
              {isSubmitting ? '확인 중…' : '확인'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

export default AuthModal;
