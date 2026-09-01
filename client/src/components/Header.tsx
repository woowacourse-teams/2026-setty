import { List } from '@phosphor-icons/react/dist/icons/List';
import { MagnifyingGlass } from '@phosphor-icons/react/dist/icons/MagnifyingGlass';
import { X } from '@phosphor-icons/react/dist/icons/X';
import { useEffect, useId, useRef, useState } from 'react';

const SEARCH_DEBOUNCE_DELAY = 150;

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
    const isSearchComposingRef = useRef(false);
    const searchDebounceTimerRef = useRef<number | null>(null);

    const clearSearchDebounce = () => {
        if (searchDebounceTimerRef.current !== null) {
            window.clearTimeout(searchDebounceTimerRef.current);
            searchDebounceTimerRef.current = null;
        }
    };

    useEffect(() => {
        clearSearchDebounce();
        if (!isSearchComposingRef.current) {
            setSearchInput(searchQuery);
        }
    }, [searchQuery]);

    useEffect(() => clearSearchDebounce, []);

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

    const changeSearchInput = (value: string) => {
        setSearchInput(value);
        if (!isSearchComposingRef.current) {
            clearSearchDebounce();
            searchDebounceTimerRef.current = window.setTimeout(() => {
                searchDebounceTimerRef.current = null;
                onSearchQueryChange(value);
            }, SEARCH_DEBOUNCE_DELAY);
        }
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
                    <div className="site-header__top-actions">
                        <button
                            className={`site-header__mobile-auth-button ${isLoggedIn ? 'site-header__mobile-logout-button' : 'site-header__mobile-login-button'}`}
                            onClick={() => runMobileAuthAction(isLoggedIn ? onLogout : onLoginClick)}
                            type="button"
                        >
                            {isLoggedIn ? '로그아웃' : '로그인'}
                        </button>
                        {isLoggedIn && (
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
                        )}
                    </div>
                </div>
                <div className="site-header__search-field">
                    <input
                        aria-label="매물 이름 검색"
                        className="site-header__search-input"
                        onChange={(event) => changeSearchInput(event.target.value)}
                        onCompositionEnd={(event) => {
                            isSearchComposingRef.current = false;
                            changeSearchInput(event.currentTarget.value);
                        }}
                        onCompositionStart={() => {
                            isSearchComposingRef.current = true;
                            clearSearchDebounce();
                        }}
                        placeholder="배송비 고민 없이 원하는 가구를 찾아보세요"
                        type="search"
                        value={searchInput}
                    />
                    <MagnifyingGlass aria-hidden="true" className="site-header__search-icon" weight="bold" />
                </div>
                <nav
                    id={menuId}
                    ref={menuRef}
                    aria-label="사용자 메뉴"
                    className={`site-header__account-menu${isMenuOpen ? ' site-header__account-menu--open' : ''}${isLoggedIn ? '' : ' site-header__account-menu--guest'}`}
                >
                    {isLoggedIn ? (
                        <>
                            <button className="site-header__my-listings" onClick={() => runMenuAction(onMyListings)} type="button">내 가구</button>
                            <button className="site-header__my-orders" onClick={() => runMenuAction(onMyOrders)} type="button">내 주문</button>
                            <i aria-hidden="true" />
                            <button className="site-header__my-account" onClick={() => runMenuAction(onMyAccount)} type="button"><strong>내 계정</strong></button>
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
