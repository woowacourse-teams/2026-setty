import { useState } from 'react';
import { useNavigate } from 'react-router';
import { toUserMessage } from '../../api/errors';
import { userRepository } from '../../api/repositories';
import type { Member } from '../../model/member';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import { clearPushToken } from '../../push/webPush';
import { resetHomeStore } from '../home/homeStore';
import {
    AlertDialog,
    BackIconButton,
    Button,
    ErrorContent,
    FlexibleTopAppBar,
    LoadingContent,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SegmentedListItem
} from '../../ui/components';
import { Icon, type IconName } from '../../ui/Icon';
import { showSnackbar } from '../../ui/snackbar';
import { shapeStyle } from '../../ui/shapes';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';

type MyPageMenu = {
    title: string;
    description?: string;
    icon: IconName;
    onClick: () => void;
};

export function MyPageScreen() {
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const { state, load } = useLoad(userRepository.getMe);
    const [isLogoutDialogVisible, setIsLogoutDialogVisible] = useState(false);
    const [isLoggingOut, setIsLoggingOut] = useState(false);
    const [isWithdrawDialogVisible, setIsWithdrawDialogVisible] = useState(false);
    const [isWithdrawing, setIsWithdrawing] = useState(false);

    const logout = async () => {
        if (isLoggingOut) return;
        setIsLoggingOut(true);
        await clearPushToken();
        await userRepository.logout();
        // 홈에 남은 프로필 첫 글자를 지우고 로그인 전 홈으로 보낸다
        resetHomeStore();
        navigate(routes.home, { replace: true });
    };

    const withdraw = async () => {
        if (isWithdrawing) return;
        setIsWithdrawing(true);
        try {
            await userRepository.withdraw();
        } catch (error) {
            showSnackbar(toUserMessage(error));
            setIsWithdrawing(false);
            return;
        }
        await clearPushToken();
        showSnackbar('탈퇴했어요. 그동안 이용해 주셔서 고마워요.', { keepOnNextScreen: true });
        resetHomeStore();
        navigate(routes.home, { replace: true });
    };

    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="내 정보" />}>
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <MyPageBody
                    member={state.data}
                    onEditProfile={() => navigate(routes.profileEdit)}
                    tradeMenus={[
                        { title: '내 판매 가구', description: '등록한 가구를 관리해요', icon: 'sell', onClick: () => navigate(routes.myListings) },
                        { title: '찜한 가구', description: '마음에 둔 가구를 모아봐요', icon: 'favorite', onClick: () => navigate(routes.favorites) },
                        { title: '주문 내역', description: '구매한 가구의 배송을 확인해요', icon: 'localShipping', onClick: () => navigate(routes.orders) }
                    ]}
                    notificationMenus={[
                        { title: '키워드 알림', description: '관심 키워드의 새 매물을 알려드려요', icon: 'tag', onClick: () => navigate(routes.keywords) },
                        { title: '알림함', description: '받은 알림을 모아봐요', icon: 'notifications', onClick: () => navigate(routes.notifications) }
                    ]}
                    accountMenus={[
                        { title: '로그아웃', icon: 'logout', onClick: () => setIsLogoutDialogVisible(true) },
                        { title: '회원 탈퇴', icon: 'delete', onClick: () => setIsWithdrawDialogVisible(true) }
                    ]}
                />
            )}
            {isLogoutDialogVisible && (
                <AlertDialog
                    confirmDisabled={isLoggingOut}
                    confirmLabel="로그아웃"
                    dismissLabel="취소"
                    onConfirm={() => {
                        setIsLogoutDialogVisible(false);
                        void logout();
                    }}
                    onDismiss={() => setIsLogoutDialogVisible(false)}
                    text="다시 이용하려면 로그인해야 합니다."
                    title="로그아웃할까요?"
                />
            )}
            {isWithdrawDialogVisible && (
                <AlertDialog
                    confirmDisabled={isWithdrawing}
                    confirmLabel="탈퇴"
                    dismissLabel="취소"
                    onConfirm={() => {
                        setIsWithdrawDialogVisible(false);
                        void withdraw();
                    }}
                    onDismiss={() => setIsWithdrawDialogVisible(false)}
                    text={'판매 중인 가구는 내려가요.\n진행 중인 거래가 끝나야 탈퇴할 수 있어요.'}
                    title="탈퇴할까요?"
                />
            )}
        </Scaffold>
    );
}

type MyPageBodyProps = {
    member: Member;
    onEditProfile: () => void;
    tradeMenus: MyPageMenu[];
    notificationMenus: MyPageMenu[];
    accountMenus: MyPageMenu[];
};

function MyPageBody({ member, onEditProfile, tradeMenus, notificationMenus, accountMenus }: MyPageBodyProps) {
    return (
        <div style={{ padding: '0 16px 24px' }}>
            <ProfileCard member={member} onEditProfile={onEditProfile} />
            <SectionTitle style={{ marginTop: 24 }}>거래</SectionTitle>
            <MenuList menus={tradeMenus} />
            <SectionTitle style={{ marginTop: 22 }}>알림</SectionTitle>
            <MenuList menus={notificationMenus} />
            <SectionTitle style={{ marginTop: 22 }}>계정</SectionTitle>
            <MenuList menus={accountMenus} />
        </div>
    );
}

function MenuList({ menus }: { menus: MyPageMenu[] }) {
    return (
        <SegmentedList>
            {menus.map((menu, index) => (
                <SegmentedListItem
                    count={menus.length}
                    headline={<span className="type-title-medium">{menu.title}</span>}
                    index={index}
                    key={menu.title}
                    leading={(
                        <span
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                width: 40,
                                height: 40,
                                background: 'var(--secondary-container)',
                                color: 'var(--on-secondary-container)',
                                ...shapeStyle('cookie6')
                            }}
                        >
                            <Icon name={menu.icon} size={20} />
                        </span>
                    )}
                    onClick={menu.onClick}
                    supporting={menu.description && <span className="type-label-medium">{menu.description}</span>}
                    trailing={<Icon name="chevronRight" />}
                />
            ))}
        </SegmentedList>
    );
}

function ProfileCard({ member, onEditProfile }: { member: Member; onEditProfile: () => void }) {
    return (
        <section className={screenStyles.card} style={{ background: 'var(--primary-container)', color: 'var(--on-primary-container)' }}>
            <div className={screenStyles.card__header}>
                <div
                    className={`${screenStyles.card__shape} type-headline-large-em`}
                    style={{ ...shapeStyle('cookie9'), background: 'var(--primary)', color: 'var(--on-primary)' }}
                >
                    {member.loginId.slice(0, 1).toUpperCase()}
                </div>
                <div className={screenStyles.card__lines}>
                    <p className="type-headline-small-em">{member.loginId}</p>
                    <p className="type-body-medium-em">{member.phoneNumber}</p>
                </div>
            </div>
            <p className="type-body-large-em" style={{ margin: 0, fontWeight: 400 }}>{member.address}</p>
            <Button fullWidth onClick={onEditProfile}>정보 수정</Button>
        </section>
    );
}
