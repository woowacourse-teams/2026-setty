import { useEffect, useState } from 'react';
import { isUnauthorized, toUserMessage } from '../api/errors';
import { AlertDialog, Button } from '../ui/components';
import { Icon } from '../ui/Icon';
import { showSnackbar } from '../ui/snackbar';
import { enablePush, getPushState, syncPushToken, type PushAccount, type PushState } from './webPush';

/** 브라우저 알림 켜기. 설정값이 없거나 지원하지 않는 브라우저면 아무것도 보여주지 않는다. */
export function PushToggle({ account, onUnauthorized }: { account: PushAccount; onUnauthorized: () => void }) {
    const [pushState, setPushState] = useState<PushState>('unsupported');
    const [isEnabling, setIsEnabling] = useState(false);
    const [isIosGuideVisible, setIsIosGuideVisible] = useState(false);

    useEffect(() => {
        void getPushState().then((state) => {
            setPushState(state);
            // 허용돼 있으면 토큰이 서버에 있는지 한 번 더 맞춘다. 같은 토큰이면 서버는 갱신만 한다
            if (state === 'granted') void syncPushToken(account);
        });
    }, [account]);

    const enable = async () => {
        if (pushState === 'ios-needs-install') {
            setIsIosGuideVisible(true);
            return;
        }
        setIsEnabling(true);
        try {
            const result = await enablePush(account);
            setPushState(result);
            if (result === 'granted') showSnackbar('브라우저 알림을 켰어요');
        } catch (error) {
            if (isUnauthorized(error)) {
                onUnauthorized();
            } else {
                showSnackbar(toUserMessage(error, '알림을 켜지 못했어요'));
            }
        } finally {
            setIsEnabling(false);
        }
    };

    if (pushState === 'unsupported') return null;
    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            {pushState === 'granted' && (
                <span className="type-label-large" style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--primary)' }}>
                    <Icon name="notifications" size={18} />브라우저 알림 켜짐
                </span>
            )}
            {pushState === 'denied' && (
                <span className="type-label-medium" style={{ color: 'var(--on-surface-variant)' }}>
                    브라우저 설정에서 알림이 차단돼 있어요
                </span>
            )}
            {(pushState === 'default' || pushState === 'ios-needs-install') && (
                <Button disabled={isEnabling} onClick={() => void enable()} variant="tonal">브라우저 알림 받기</Button>
            )}
            {isIosGuideVisible && (
                <AlertDialog
                    confirmLabel="확인"
                    dismissLabel="닫기"
                    onConfirm={() => setIsIosGuideVisible(false)}
                    onDismiss={() => setIsIosGuideVisible(false)}
                    text="사파리 공유 버튼 → 홈 화면에 추가 → 추가된 세티를 열고 다시 눌러 주세요."
                    title="홈 화면에 추가한 뒤 켤 수 있어요"
                />
            )}
        </div>
    );
}
