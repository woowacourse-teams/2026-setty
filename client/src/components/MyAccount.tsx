import { useEffect, useState } from 'react';
import { fetchMe, updateProfile, type MemberMe } from '../api/auth';
import { cx } from '../styles/classNames';
import accountPagesStyles from '../styles/modules/AccountPages.module.css';
import productGridStyles from '../styles/modules/ProductGrid.module.css';
import authModalStyles from '../styles/modules/AuthModal.module.css';

type MyAccountProps = {
    onFavorites: () => void;
    onOrders: () => void;
    onListings: () => void;
};

export function MyAccount({ onFavorites, onOrders, onListings }: MyAccountProps) {
    const [me, setMe] = useState<MemberMe | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isEditing, setIsEditing] = useState(false);
    const [phoneNumber, setPhoneNumber] = useState('');
    const [address, setAddress] = useState('');
    const [isSaving, setIsSaving] = useState(false);
    const [editError, setEditError] = useState<string | null>(null);

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

    const startEdit = () => {
        if (!me) return;
        setPhoneNumber(me.phoneNumber);
        setAddress(me.address);
        setEditError(null);
        setIsEditing(true);
    };

    const save = async (event: React.FormEvent) => {
        event.preventDefault();
        setIsSaving(true);
        setEditError(null);
        try {
            setMe(await updateProfile({ phoneNumber, address }));
            setIsEditing(false);
        } catch (reason) {
            setEditError(reason instanceof Error ? reason.message : '회원 정보를 수정하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <section className={cx(accountPagesStyles['my-orders'], accountPagesStyles['my-account'])} aria-labelledby="my-account-title">
            <div className={cx(accountPagesStyles['my-orders__heading'])}>
                <h1 id="my-account-title">내 계정</h1>
            </div>
            {isLoading && <p className={cx(productGridStyles['product-grid-message'])}>내 정보를 불러오는 중입니다.</p>}
            {error && <div className={cx(productGridStyles['product-grid-message'])}><p>{error}</p><button onClick={() => void load()} type="button">다시 시도</button></div>}
            {!isLoading && !error && me && !isEditing && (
                <>
                    <dl className={cx(accountPagesStyles['my-account__profile'])}>
                        <div><dt>아이디</dt><dd>{me.loginId}</dd></div>
                        <div><dt>연락처</dt><dd>{me.phoneNumber}</dd></div>
                        <div><dt>주소</dt><dd>{me.address}</dd></div>
                    </dl>
                    <div className={cx(accountPagesStyles['my-account__actions'])}>
                        <button className={cx(accountPagesStyles['my-account__edit-toggle'])} onClick={startEdit} type="button">정보 수정</button>
                    </div>
                    <nav className={cx(accountPagesStyles['my-account__links'])} aria-label="내 활동 바로가기">
                        <button onClick={onFavorites} type="button">찜한 매물</button>
                        <button onClick={onOrders} type="button">내 주문</button>
                        <button onClick={onListings} type="button">내 가구</button>
                    </nav>
                </>
            )}
            {!isLoading && !error && me && isEditing && (
                <form className={cx(authModalStyles['auth-modal__form'], accountPagesStyles['my-account__edit'])} onSubmit={(event) => void save(event)}>
                    <label className={cx(authModalStyles['auth-modal__field'])}>
                        <span>연락처</span>
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
                    <label className={cx(authModalStyles['auth-modal__field'])}>
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
                    {editError && <p className={cx(authModalStyles['auth-modal__feedback'], authModalStyles['auth-modal__feedback--error'])} role="alert">{editError}</p>}
                    <div className={cx(accountPagesStyles['my-account__edit-actions'])}>
                        <button className={cx(accountPagesStyles['my-account__edit-cancel'])} disabled={isSaving} onClick={() => setIsEditing(false)} type="button">취소</button>
                        <button className={cx(authModalStyles['auth-modal__submit'], accountPagesStyles['my-account__edit-submit'])} disabled={isSaving} type="submit">{isSaving ? '저장 중...' : '저장'}</button>
                    </div>
                </form>
            )}
        </section>
    );
}
