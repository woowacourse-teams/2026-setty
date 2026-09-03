import { useEffect, useRef, useState } from 'react';
import { fetchMe, updateProfile, type MemberMe } from '../api/auth';
import accountPagesStyles from '../styles/modules/AccountPages.module.css';
import productGridStyles from '../styles/modules/ProductGrid.module.css';
import authModalStyles from '../styles/modules/AuthModal.module.css';

type MyAccountProps = {
    onFavorites: () => void;
    onOrders: () => void;
    onListings: () => void;
};

function formatPhoneNumber(value: string) {
    const digits = value.replace(/\D/g, '').slice(0, 11);
    if (digits.length <= 3) return digits;
    if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
    return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}

export function MyAccount({ onFavorites, onOrders, onListings }: MyAccountProps) {
    const editButtonRef = useRef<HTMLButtonElement>(null);
    const phoneInputRef = useRef<HTMLInputElement>(null);
    const [me, setMe] = useState<MemberMe | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isEditing, setIsEditing] = useState(false);
    const [phoneNumber, setPhoneNumber] = useState('');
    const [address, setAddress] = useState('');
    const [isSaving, setIsSaving] = useState(false);
    const [editError, setEditError] = useState<string | null>(null);
    const [saveMessage, setSaveMessage] = useState<string | null>(null);

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
        setSaveMessage(null);
        setIsEditing(true);
        window.requestAnimationFrame(() => phoneInputRef.current?.focus());
    };

    const finishEdit = () => {
        setIsEditing(false);
        window.requestAnimationFrame(() => editButtonRef.current?.focus());
    };

    const save = async (event: React.FormEvent) => {
        event.preventDefault();
        setIsSaving(true);
        setEditError(null);
        try {
            setMe(await updateProfile({ phoneNumber, address }));
            setSaveMessage('회원 정보가 수정되었습니다.');
            finishEdit();
        } catch (reason) {
            setEditError(reason instanceof Error ? reason.message : '회원 정보를 수정하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <section className={[accountPagesStyles['my-orders'], accountPagesStyles['my-account']].filter(Boolean).join(' ')} aria-labelledby="my-account-title">
            <div className={accountPagesStyles['my-orders__heading']}>
                <h1 id="my-account-title">내 계정</h1>
            </div>
            {isLoading && <p className={productGridStyles['product-grid-message']} role="status">내 정보를 불러오는 중입니다.</p>}
            {error && <div className={productGridStyles['product-grid-message']} role="alert"><p>{error}</p><button onClick={() => void load()} type="button">다시 시도</button></div>}
            {!isLoading && !error && me && !isEditing && (
                <>
                    <dl className={accountPagesStyles['my-account__profile']}>
                        <div><dt>아이디</dt><dd>{me.loginId}</dd></div>
                        <div><dt>연락처</dt><dd>{me.phoneNumber}</dd></div>
                        <div><dt>주소</dt><dd>{me.address}</dd></div>
                    </dl>
                    <div className={accountPagesStyles['my-account__actions']}>
                        <button className={accountPagesStyles['my-account__edit-toggle']} onClick={startEdit} ref={editButtonRef} type="button">정보 수정</button>
                    </div>
                    {saveMessage && <p className={accountPagesStyles['my-account__status']} role="status">{saveMessage}</p>}
                    <nav className={accountPagesStyles['my-account__links']} aria-label="내 활동 바로가기">
                        <a href="/my-favorites" onClick={(event) => { event.preventDefault(); onFavorites(); }}>찜한 매물</a>
                        <a href="/my-orders" onClick={(event) => { event.preventDefault(); onOrders(); }}>내 주문</a>
                        <a href="/my-listings" onClick={(event) => { event.preventDefault(); onListings(); }}>내 가구</a>
                    </nav>
                </>
            )}
            {!isLoading && !error && me && isEditing && (
                <form aria-busy={isSaving} className={[authModalStyles['auth-modal__form'], accountPagesStyles['my-account__edit']].filter(Boolean).join(' ')} onSubmit={(event) => void save(event)}>
                    <label className={authModalStyles['auth-modal__field']}>
                        <span>연락처</span>
                        <input
                            autoComplete="tel"
                            inputMode="tel"
                            maxLength={13}
                            name="phoneNumber"
                            onChange={(event) => setPhoneNumber(formatPhoneNumber(event.target.value))}
                            pattern="010-[0-9]{4}-[0-9]{4}"
                            placeholder="010-0000-0000"
                            ref={phoneInputRef}
                            required
                            title="010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요."
                            type="tel"
                            value={phoneNumber}
                        />
                    </label>
                    <label className={authModalStyles['auth-modal__field']}>
                        <span>주소</span>
                        <input
                            autoComplete="street-address"
                            maxLength={200}
                            name="address"
                            onChange={(event) => setAddress(event.target.value)}
                            placeholder="주소를 입력해 주세요"
                            required
                            type="text"
                            value={address}
                        />
                    </label>
                    {editError && <p className={[authModalStyles['auth-modal__feedback'], authModalStyles['auth-modal__feedback--error']].filter(Boolean).join(' ')} role="alert">{editError}</p>}
                    <div className={accountPagesStyles['my-account__edit-actions']}>
                        <button className={accountPagesStyles['my-account__edit-cancel']} disabled={isSaving} onClick={finishEdit} type="button">취소</button>
                        <button className={[authModalStyles['auth-modal__submit'], accountPagesStyles['my-account__edit-submit']].filter(Boolean).join(' ')} disabled={isSaving} type="submit">{isSaving ? '저장 중...' : '저장'}</button>
                    </div>
                </form>
            )}
        </section>
    );
}
