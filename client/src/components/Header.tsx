function SearchIcon() {
    return (
        <svg aria-hidden="true" className="site-header__search-icon" viewBox="0 0 24 24">
            <circle cx="10.8" cy="10.8" r="6.3" />
            <path d="m16 16 4.2 4.2" />
        </svg>
    );
}

interface HeaderProps {
    onHome: () => void;
    onMyListings: () => void;
    isLoggedIn: boolean;
    onLogout: () => void;
    onLoginClick: () => void;
}

export function Header({ onHome, onMyListings, isLoggedIn, onLogout, onLoginClick }: HeaderProps) {
    return (
        <header className="site-header">
            <div className="site-header__content">
                <a
                    className="site-header__wordmark"
                    href="/"
                    aria-label="SETTY 홈"
                    onClick={(event) => {
                        event.preventDefault();
                        onHome();
                    }}
                >
                    SETTY
                </a>
                <div className="site-header__search-field" role="search">
                    <span className="site-header__search-field-placeholder">배송비 고민 없이 원하는 가구를 찾아보세요</span>
                    <SearchIcon />
                </div>
                {isLoggedIn ? (
                    <nav aria-label="사용자 메뉴" className="site-header__account-menu">
                        <button className="site-header__my-listings" onClick={onMyListings} type="button">내 가구</button>
                        <i aria-hidden="true" />
                        <strong>내 계정</strong>
                        <button className="site-header__logout-button" onClick={onLogout} type="button">로그아웃</button>
                    </nav>
                ) : <button className="site-header__login-button" onClick={onLoginClick} type="button">로그인</button>}
            </div>
        </header>
    );
}
