import { List } from '@phosphor-icons/react/dist/icons/List';
import { MagnifyingGlass } from '@phosphor-icons/react/dist/icons/MagnifyingGlass';
import { X } from '@phosphor-icons/react/dist/icons/X';
import { useEffect, useId, useRef, useState } from 'react';

interface HeaderProps {
    onHome: () => void;
    onMyListings: () => void;
    onMyOrders: () => void;
    isLoggedIn: boolean;
    onLogout: () => void;
    onLoginClick: () => void;
}

export function Header({ onHome, onMyListings, onMyOrders, isLoggedIn, onLogout, onLoginClick }: HeaderProps) {
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const menuId = useId();
    const menuButtonRef = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        if (!isMenuOpen) {
            return;
        }

        const closeMenuOnEscape = (event: KeyboardEvent) => {
            if (event.key !== 'Escape') {
                return;
            }

            setIsMenuOpen(false);
            menuButtonRef.current?.focus();
        };

        window.addEventListener('keydown', closeMenuOnEscape);
        return () => window.removeEventListener('keydown', closeMenuOnEscape);
    }, [isMenuOpen]);

    const runMenuAction = (action: () => void) => {
        const shouldRestoreMenuFocus = isMenuOpen;
        setIsMenuOpen(false);
        if (shouldRestoreMenuFocus) {
            menuButtonRef.current?.focus();
        }
        action();
    };

    return (
        <header className="site-header">
            <div className="site-header__content">
                <div className="site-header__top-row">
                    <a
                        className="site-header__wordmark"
                        href="/"
                        aria-label="SETTY 홈"
                        onClick={(event) => {
                            event.preventDefault();
                            runMenuAction(onHome);
                        }}
                    >
                        SETTY
                    </a>
                    <button
                        ref={menuButtonRef}
                        className="site-header__menu-button"
                        type="button"
                        aria-controls={menuId}
                        aria-expanded={isMenuOpen}
                        onClick={() => setIsMenuOpen((isOpen) => !isOpen)}
                    >
                        {isMenuOpen
                            ? <X aria-hidden="true" className="site-header__menu-icon" weight="bold" />
                            : <List aria-hidden="true" className="site-header__menu-icon" weight="bold" />}
                        <span className="site-header__visually-hidden">{isMenuOpen ? '메뉴 닫기' : '메뉴 열기'}</span>
                    </button>
                </div>
                <div className="site-header__search-field">
                    <span className="site-header__search-field-placeholder">배송비 고민 없이 원하는 가구를 찾아보세요</span>
                    <MagnifyingGlass aria-hidden="true" className="site-header__search-icon" weight="bold" />
                </div>
                <nav
                    id={menuId}
                    aria-label="사용자 메뉴"
                    className={`site-header__account-menu${isMenuOpen ? ' site-header__account-menu--open' : ''}${isLoggedIn ? '' : ' site-header__account-menu--guest'}`}
                >
                    {isLoggedIn ? (
                        <>
                            <button className="site-header__my-listings" onClick={() => runMenuAction(onMyListings)} type="button">내 가구</button>
                            <button onClick={() => runMenuAction(onMyOrders)} type="button">내 주문</button>
                            <i aria-hidden="true" />
                            <strong>내 계정</strong>
                            <button className="site-header__logout-button" onClick={() => runMenuAction(onLogout)} type="button">로그아웃</button>
                        </>
                    ) : (
                        <button className="site-header__login-button" onClick={() => runMenuAction(onLoginClick)} type="button">로그인</button>
                    )}
                </nav>
            </div>
        </header>
    );
}
