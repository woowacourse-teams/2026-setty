import { useEffect, useState } from 'react';
import { fetchMe, type MemberMe } from '../api/auth';

type MyAccountProps = {
    onFavorites: () => void;
    onOrders: () => void;
    onListings: () => void;
};

export function MyAccount({ onFavorites, onOrders, onListings }: MyAccountProps) {
    const [me, setMe] = useState<MemberMe | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = async () => {
        setIsLoading(true);
        setError(null);
        try {
            setMe(await fetchMe());
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '내 정보를 불러오지 못했습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { void load(); }, []);

    return (
        <section className="my-orders my-account" aria-labelledby="my-account-title">
            <div className="my-orders__heading">
                <h1 id="my-account-title">내 계정</h1>
            </div>
            {isLoading && <p className="product-grid-message">내 정보를 불러오는 중입니다.</p>}
            {error && <div className="product-grid-message"><p>{error}</p><button onClick={() => void load()} type="button">다시 시도</button></div>}
            {!isLoading && !error && me && (
                <>
                    <dl className="my-account__profile">
                        <div><dt>아이디</dt><dd>{me.loginId}</dd></div>
                        <div><dt>연락처</dt><dd>{me.phoneNumber}</dd></div>
                        <div><dt>주소</dt><dd>{me.address}</dd></div>
                    </dl>
                    <nav className="my-account__links" aria-label="내 활동 바로가기">
                        <button onClick={onFavorites} type="button">찜한 매물</button>
                        <button onClick={onOrders} type="button">내 주문</button>
                        <button onClick={onListings} type="button">내 가구</button>
                    </nav>
                </>
            )}
        </section>
    );
}
