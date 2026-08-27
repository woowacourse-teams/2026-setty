import { FormEvent, useEffect, useState } from 'react';
import { AuthApiError, login, signup } from '../api/auth';

type AuthMode = 'login' | 'signup';
type Feedback = {
    message: string;
    type: 'error' | 'success';
};

interface AuthModalProps {
    onClose: () => void;
}

function CloseIcon() {
    return <span aria-hidden="true">×</span>;
}

export function AuthModal({ onClose }: AuthModalProps) {
    const [mode, setMode] = useState<AuthMode>('login');
    const [loginId, setLoginId] = useState('');
    const [password, setPassword] = useState('');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [address, setAddress] = useState('');
    const [feedback, setFeedback] = useState<Feedback | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') onClose();
        };

        window.addEventListener('keydown', closeOnEscape);
        return () => window.removeEventListener('keydown', closeOnEscape);
    }, [onClose]);

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
                setFeedback({ type: 'success', message: '로그인되었습니다.' });
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
        <div className="auth-modal-backdrop" role="presentation" onMouseDown={onClose}>
            <section
                aria-labelledby="auth-modal-title"
                aria-modal="true"
                className={`auth-modal auth-modal--${mode}`}
                role="dialog"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <button aria-label="닫기" className="auth-modal__close" onClick={onClose} type="button">
                    <CloseIcon />
                </button>

                <div className="auth-modal__brand">
                    <h1 id="auth-modal-title">SETTY</h1>
                    <p>배송까지 끝내는 중고 가구 거래</p>
                </div>

                <div className="auth-modal__tabs" role="tablist" aria-label="인증 방식">
                    <button
                        aria-selected={isLogin}
                        className={`auth-modal__tab ${isLogin ? 'auth-modal__tab--active' : ''}`}
                        onClick={() => changeMode('login')}
                        role="tab"
                        type="button"
                    >
                        로그인
                    </button>
                    <button
                        aria-selected={!isLogin}
                        className={`auth-modal__tab ${!isLogin ? 'auth-modal__tab--active' : ''}`}
                        onClick={() => changeMode('signup')}
                        role="tab"
                        type="button"
                    >
                        회원가입
                    </button>
                </div>

                <form className="auth-modal__form" onSubmit={(event) => void handleSubmit(event)}>
                    <label className="auth-modal__field">
                        <span>아이디</span>
                        <input
                            autoComplete="username"
                            name="loginId"
                            onChange={(event) => setLoginId(event.target.value)}
                            placeholder="영문 소문자·숫자 4~20자"
                            required
                            type="text"
                            value={loginId}
                        />
                    </label>
                    <label className="auth-modal__field">
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
                            <label className="auth-modal__field">
                                <span>휴대폰 번호</span>
                                <input
                                    autoComplete="tel"
                                    inputMode="tel"
                                    name="phoneNumber"
                                    onChange={(event) => setPhoneNumber(event.target.value)}
                                    placeholder="010-0000-0000"
                                    required
                                    type="tel"
                                    value={phoneNumber}
                                />
                            </label>
                            <label className="auth-modal__field">
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
                            className={`auth-modal__feedback auth-modal__feedback--${feedback.type}`}
                            role={feedback.type === 'error' ? 'alert' : 'status'}
                        >
                            {feedback.message}
                        </p>
                    )}
                    <button className="auth-modal__submit" disabled={isSubmitting} type="submit">
                        {isSubmitting ? '처리 중...' : isLogin ? '로그인' : '가입하고 시작하기'}
                    </button>
                </form>
            </section>
        </div>
    );
}
