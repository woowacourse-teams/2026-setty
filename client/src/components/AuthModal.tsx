import { X } from '@phosphor-icons/react/dist/icons/X';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { AuthApiError, login, signup } from '../api/auth';
import authModalStyles from '../styles/modules/AuthModal.module.css';

type AuthMode = 'login' | 'signup';
type Feedback = {
    message: string;
    type: 'error' | 'success';
};

interface AuthModalProps {
    onClose: () => void;
    onLoggedIn: () => void;
}

function formatPhoneNumber(value: string) {
    const digits = value.replace(/\D/g, '').slice(0, 11);
    if (digits.length <= 3) return digits;
    if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
    return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}

const focusableElementSelector = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])'
].join(',');

export function AuthModal({ onClose, onLoggedIn }: AuthModalProps) {
    const dialogRef = useRef<HTMLElement>(null);
    const loginIdInputRef = useRef<HTMLInputElement>(null);
    const onCloseRef = useRef(onClose);
    const [mode, setMode] = useState<AuthMode>('login');
    const [loginId, setLoginId] = useState('');
    const [password, setPassword] = useState('');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [address, setAddress] = useState('');
    const [feedback, setFeedback] = useState<Feedback | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        onCloseRef.current = onClose;
    }, [onClose]);

    useEffect(() => {
        const previouslyFocusedElement = document.activeElement instanceof HTMLElement
            ? document.activeElement
            : null;
        const previousBodyOverflow = document.body.style.overflow;

        document.body.style.overflow = 'hidden';
        loginIdInputRef.current?.focus();

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                event.preventDefault();
                onCloseRef.current();
                return;
            }

            if (event.key !== 'Tab') return;

            const dialog = dialogRef.current;
            if (!dialog) return;

            const focusableElements = Array.from(
                dialog.querySelectorAll<HTMLElement>(focusableElementSelector)
            ).filter((element) => element.getAttribute('aria-hidden') !== 'true');

            if (focusableElements.length === 0) {
                event.preventDefault();
                dialog.focus();
                return;
            }

            const firstElement = focusableElements[0];
            const lastElement = focusableElements[focusableElements.length - 1];
            const activeElement = document.activeElement;

            if (event.shiftKey && (activeElement === firstElement || !dialog.contains(activeElement))) {
                event.preventDefault();
                lastElement.focus();
            } else if (!event.shiftKey && (activeElement === lastElement || !dialog.contains(activeElement))) {
                event.preventDefault();
                firstElement.focus();
            }
        };

        window.addEventListener('keydown', handleKeyDown);

        return () => {
            window.removeEventListener('keydown', handleKeyDown);
            document.body.style.overflow = previousBodyOverflow;

            if (previouslyFocusedElement?.isConnected) {
                previouslyFocusedElement.focus({ preventScroll: true });
            }
        };
    }, []);

    const changeMode = (nextMode: AuthMode) => {
        setMode(nextMode);
        setPassword('');
        setFeedback(null);
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setFeedback(null);
        setIsSubmitting(true);

        try {
            if (mode === 'login') {
                const response = await login({ loginId, password });
                window.sessionStorage.setItem('setty:auth-token', response.token);
                window.sessionStorage.setItem('setty:auth-role', response.role);
                onLoggedIn();
                return;
            }

            const response = await signup({ loginId, password, phoneNumber, address });
            setLoginId(response.loginId);
            setPassword('');
            setMode('login');
            setFeedback({ type: 'success', message: '회원가입이 완료되었습니다. 로그인해 주세요.' });
        } catch (error) {
            const message = error instanceof AuthApiError
                ? error.message
                : mode === 'login' ? '로그인에 실패했습니다.' : '회원가입에 실패했습니다.';
            setFeedback({ type: 'error', message });
        } finally {
            setIsSubmitting(false);
        }
    };

    const isLogin = mode === 'login';

    return (
        <div className={authModalStyles['auth-modal-backdrop']} role="presentation" onMouseDown={onClose}>
            <section
                aria-labelledby="auth-modal-title"
                aria-modal="true"
                className={[authModalStyles['auth-modal'], authModalStyles[`auth-modal--${mode}`]].filter(Boolean).join(' ')}
                ref={dialogRef}
                role="dialog"
                tabIndex={-1}
                onMouseDown={(event) => event.stopPropagation()}
            >
                <button aria-label="닫기" className={authModalStyles['auth-modal__close']} onClick={onClose} type="button">
                    <X aria-hidden="true" size={28} weight="light" />
                </button>

                <div className={authModalStyles['auth-modal__brand']}>
                    <h1 id="auth-modal-title">SETTY</h1>
                    <p>배송까지 끝내는 중고 가구 거래</p>
                </div>

                <div className={authModalStyles['auth-modal__tabs']} role="group" aria-label="인증 방식">
                    <button
                        aria-pressed={isLogin}
                        className={[authModalStyles['auth-modal__tab'], isLogin && authModalStyles['auth-modal__tab--active']].filter(Boolean).join(' ')}
                        onClick={() => changeMode('login')}
                        type="button"
                    >
                        로그인
                    </button>
                    <button
                        aria-pressed={!isLogin}
                        className={[authModalStyles['auth-modal__tab'], !isLogin && authModalStyles['auth-modal__tab--active']].filter(Boolean).join(' ')}
                        onClick={() => changeMode('signup')}
                        type="button"
                    >
                        회원가입
                    </button>
                </div>

                <form className={authModalStyles['auth-modal__form']} onSubmit={(event) => void handleSubmit(event)}>
                    <label className={authModalStyles['auth-modal__field']}>
                        <span>아이디</span>
                        <input
                            autoComplete="username"
                            name="loginId"
                            onChange={(event) => setLoginId(event.target.value)}
                            placeholder="영문 소문자·숫자 4~20자"
                            ref={loginIdInputRef}
                            required
                            type="text"
                            value={loginId}
                        />
                    </label>
                    <label className={authModalStyles['auth-modal__field']}>
                        <span>비밀번호</span>
                        <input
                            autoComplete={isLogin ? 'current-password' : 'new-password'}
                            name="password"
                            onChange={(event) => setPassword(event.target.value)}
                            placeholder="8자 이상"
                            required
                            type="password"
                            value={password}
                        />
                    </label>
                    {!isLogin && (
                        <>
                            <label className={authModalStyles['auth-modal__field']}>
                                <span>휴대폰 번호</span>
                                <input
                                    autoComplete="tel"
                                    inputMode="tel"
                                    name="phoneNumber"
                                    onChange={(event) => setPhoneNumber(formatPhoneNumber(event.target.value))}
                                    placeholder="010-0000-0000"
                                    required
                                    type="tel"
                                    value={phoneNumber}
                                />
                            </label>
                            <label className={authModalStyles['auth-modal__field']}>
                                <span>주소</span>
                                <input
                                    autoComplete="street-address"
                                    name="address"
                                    onChange={(event) => setAddress(event.target.value)}
                                    placeholder="주소를 입력해 주세요"
                                    required
                                    type="text"
                                    value={address}
                                />
                            </label>
                        </>
                    )}
                    {feedback && (
                        <p
                            aria-live="polite"
                            className={[authModalStyles['auth-modal__feedback'], authModalStyles[`auth-modal__feedback--${feedback.type}`]].filter(Boolean).join(' ')}
                            role={feedback.type === 'error' ? 'alert' : 'status'}
                        >
                            {feedback.message}
                        </p>
                    )}
                    <button className={authModalStyles['auth-modal__submit']} disabled={isSubmitting} type="submit">
                        {isSubmitting ? '처리 중...' : isLogin ? '로그인' : '가입하고 시작하기'}
                    </button>
                </form>
            </section>
        </div>
    );
}
