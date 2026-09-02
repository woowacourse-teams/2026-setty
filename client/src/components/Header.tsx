import { List } from '@phosphor-icons/react/dist/icons/List';
import { MagnifyingGlass } from '@phosphor-icons/react/dist/icons/MagnifyingGlass';
import { X } from '@phosphor-icons/react/dist/icons/X';
import { useEffect, useId, useRef, useState } from 'react';
import layoutStyles from '../styles/modules/Layout.module.css';

interface HeaderProps {
    onHome: () => void;
    onSearchQueryChange: (query: string) => void;
    onMyListings: () => void;
    onMyOrders: () => void;
    onMyAccount: () => void;
    isLoggedIn: boolean;
    onLogout: () => void;
    onLoginClick: () => void;
    searchQuery: string;
}

export function Header({
    onHome,
    onSearchQueryChange,
    onMyListings,
    onMyOrders,
    onMyAccount,
    isLoggedIn,
    onLogout,
    onLoginClick,
    searchQuery
}: HeaderProps) {
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const [searchInput, setSearchInput] = useState(searchQuery);
    const menuId = useId();
    const menuButtonRef = useRef<HTMLButtonElement>(null);
    const menuRef = useRef<HTMLElement>(null);
    const searchInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        setSearchInput(searchQuery);
    }, [searchQuery]);

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

        const closeMenuOnOutsideClick = (event: PointerEvent) => {
            if (!(event.target instanceof Node)) {
                return;
            }

            if (menuRef.current?.contains(event.target) || menuButtonRef.current?.contains(event.target)) {
                return;
            }

            setIsMenuOpen(false);
        };

        window.addEventListener('keydown', closeMenuOnEscape);
        document.addEventListener('pointerdown', closeMenuOnOutsideClick);
        return () => {
            window.removeEventListener('keydown', closeMenuOnEscape);
            document.removeEventListener('pointerdown', closeMenuOnOutsideClick);
        };
    }, [isMenuOpen]);

    const runMenuAction = (action: () => void) => {
        const shouldRestoreMenuFocus = isMenuOpen;
        setIsMenuOpen(false);
        if (shouldRestoreMenuFocus) {
            menuButtonRef.current?.focus();
        }
        action();
    };

    const runMobileAuthAction = (action: () => void) => {
        setIsMenuOpen(false);
        action();
    };

    const submitSearch = () => {
        onSearchQueryChange(searchInputRef.current?.value ?? searchInput);
    };

    return (
        <header className={layoutStyles['site-header']}>
            <div className={layoutStyles['site-header__content']}>
                <div className={layoutStyles['site-header__top-row']}>
                    <a
                        className={layoutStyles['site-header__wordmark']}
                        href="/"
                        aria-label="SETTY 홈"
                        onClick={(event) => {
                            event.preventDefault();
                            runMenuAction(onHome);
                        }}
                    >
                        SETTY
                    </a>
                    <div className={layoutStyles['site-header__top-actions']}>
                        {!isLoggedIn && (
                            <button
                                className={layoutStyles['site-header__mobile-auth-button']}
                                onClick={() => runMobileAuthAction(onLoginClick)}
                                type="button"
                            >
                                로그인
                            </button>
                        )}
                        {isLoggedIn && (
                            <button
                                ref={menuButtonRef}
                                className={layoutStyles['site-header__menu-button']}
                                type="button"
                                aria-controls={menuId}
                                aria-expanded={isMenuOpen}
                                onClick={() => setIsMenuOpen((isOpen) => !isOpen)}
                            >
                                {isMenuOpen
                                    ? <X aria-hidden="true" className={layoutStyles['site-header__menu-icon']} weight="bold" />
                                    : <List aria-hidden="true" className={layoutStyles['site-header__menu-icon']} weight="bold" />}
                                <span className={layoutStyles['site-header__visually-hidden']}>{isMenuOpen ? '메뉴 닫기' : '메뉴 열기'}</span>
                            </button>
                        )}
                    </div>
                </div>
                <form
                    className={layoutStyles['site-header__search-field']}
                    onSubmit={(event) => {
                        event.preventDefault();
                        submitSearch();
                    }}
                >
                    <input
                        aria-label="매물 이름 검색"
                        className={layoutStyles['site-header__search-input']}
                        onChange={(event) => setSearchInput(event.target.value)}
                        placeholder="배송비 고민 없이 원하는 가구를 찾아보세요"
                        ref={searchInputRef}
                        type="text"
                        value={searchInput}
                    />
                    <button aria-label="매물 검색" className={layoutStyles['site-header__search-button']} type="submit">
                        <MagnifyingGlass aria-hidden="true" className={layoutStyles['site-header__search-icon']} weight="bold" />
                    </button>
                </form>
                <nav
                    id={menuId}
                    ref={menuRef}
                    aria-label="사용자 메뉴"
                    className={[layoutStyles['site-header__account-menu'], isMenuOpen && layoutStyles['site-header__account-menu--open'], !isLoggedIn && layoutStyles['site-header__account-menu--guest']].filter(Boolean).join(' ')}
                >
                    {isLoggedIn ? (
                        <>
                            <button onClick={() => runMenuAction(onMyListings)} type="button">내 가구</button>
                            <button onClick={() => runMenuAction(onMyOrders)} type="button">내 주문</button>
                            <i aria-hidden="true" />
                            <button onClick={() => runMenuAction(onMyAccount)} type="button"><strong>내 계정</strong></button>
                            <button className={layoutStyles['site-header__logout-button']} onClick={() => runMenuAction(onLogout)} type="button">로그아웃</button>
                        </>
                    ) : (
                        <button className={layoutStyles['site-header__login-button']} onClick={() => runMenuAction(onLoginClick)} type="button">로그인</button>
                    )}
                </nav>
            </div>
        </header>
    );
}
