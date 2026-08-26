import { FormEvent, useEffect, useState } from 'react';

type AuthMode = 'login' | 'signup';

interface AuthModalProps {
    onClose: () => void;
}

function CloseIcon() {
    return <span aria-hidden="true">×</span>;
}

export function AuthModal({ onClose }: AuthModalProps) {
    const [mode, setMode] = useState<AuthMode>('login');

    useEffect(() => {
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') onClose();
        };

        window.addEventListener('keydown', closeOnEscape);
        return () => window.removeEventListener('keydown', closeOnEscape);
    }, [onClose]);

    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
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
                        onClick={() => setMode('login')}
                        role="tab"
                        type="button"
                    >
                        로그인
                    </button>
                    <button
                        aria-selected={!isLogin}
                        className={`auth-modal__tab ${!isLogin ? 'auth-modal__tab--active' : ''}`}
                        onClick={() => setMode('signup')}
                        role="tab"
                        type="button"
                    >
                        회원가입
                    </button>
                </div>

                <form className="auth-modal__form" onSubmit={handleSubmit}>
                    {!isLogin && (
                        <label className="auth-modal__field">
                            <span>이름</span>
                            <input autoComplete="name" name="name" placeholder="홍길동" type="text" />
                        </label>
                    )}
                    <label className="auth-modal__field">
                        <span>이메일</span>
                        <input autoComplete="email" name="email" placeholder="setty@example.com" type="email" />
                    </label>
                    <label className="auth-modal__field">
                        <span>비밀번호</span>
                        <input autoComplete={isLogin ? 'current-password' : 'new-password'} name="password" placeholder="8자 이상" type="password" />
                    </label>
                    {!isLogin && (
                        <label className="auth-modal__field">
                            <span>휴대폰 번호</span>
                            <input autoComplete="tel" inputMode="tel" name="phone" placeholder="010-0000-0000" type="tel" />
                        </label>
                    )}
                    <button className="auth-modal__submit" type="submit">
                        {isLogin ? '로그인' : '가입하고 시작하기'}
                    </button>
                </form>
            </section>
        </div>
    );
}
