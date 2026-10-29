import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { identifyUser } from '../../analytics/posthog';
import { ApiError, NetworkError } from '../../api/http';
import { deliveryRepository } from '../../api/deliveryRepository';
import { userRepository } from '../../api/repositories';
import { accountRoleOf, routes } from '../../navigation';
import { BottomBar, Button, LoadingIndicator, Scaffold, TextField } from '../../ui/components';
import { AccountRoleToggle } from './AccountRoleToggle';
import { showSnackbar } from '../../ui/snackbar';
import { resetHomeStore } from '../home/homeStore';
import styles from '../screens.module.css';

function loginErrorMessage(error: unknown): string {
    if (error instanceof ApiError) {
        return error.code === 'LOGIN_FAILED' || error.code === 'INVALID_REQUEST'
            ? '올바르지 않은 정보입니다'
            : '알 수 없는 에러가 발생했습니다';
    }
    if (error instanceof NetworkError) return '네트워크 에러가 발생했습니다';
    return '알 수 없는 에러가 발생했습니다';
}

export function LoginScreen() {
    const navigate = useNavigate();
    const role = accountRoleOf(useLocation().search);
    const isDriver = role === 'driver';
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const login = async () => {
        if (isLoading) return;
        setIsLoading(true);
        try {
            if (isDriver) {
                await deliveryRepository.login(username, password);
                navigate(routes.driverRequests, { replace: true });
                return;
            }
            await userRepository.login(username, password);
            identifyUser(username);
            // 다른 계정으로 다시 로그인했을 수 있으므로 이전 홈 목록을 버린다
            resetHomeStore();
            navigate(routes.home, { replace: true });
        } catch (error) {
            setIsLoading(false);
            showSnackbar(loginErrorMessage(error));
        }
    };

    return (
        <Scaffold
            bottomBar={(
                <BottomBar wide>
                    {isLoading ? <LoadingIndicator /> : (
                        <Button fullWidth onClick={() => void login()} size="medium">로그인</Button>
                    )}
                </BottomBar>
            )}
        >
            <div className={styles['form-column']}>
                <AccountRoleToggle path={routes.login} role={role} />
                <h1 className="type-headline-large-em" style={{ margin: 0 }}>{isDriver ? 'SETTY 배송원 로그인' : 'SETTY 로그인'}</h1>
                {isDriver ? (
                    <p className={`${styles.lead} type-body-large-em`}>
                        <span style={{ fontWeight: 900 }}>가까운 가구 배송</span>을 골라 <span style={{ fontWeight: 200 }}>가볍게</span>
                        {'\n'}세티 배송원으로 <span style={{ fontWeight: 600 }}>배차를 시작</span>해보세요.
                    </p>
                ) : (
                    <p className={`${styles.lead} type-body-large-em`}>
                        <span style={{ fontWeight: 900 }}>무거운 중고 가구</span>를 더 <span style={{ fontWeight: 200 }}>가볍게</span>
                        {'\n'}세티와 함께 <span style={{ fontWeight: 600 }}>중고 가구 거래</span>를 시작해보세요.
                    </p>
                )}
                <TextField
                    autoComplete="username"
                    enterKeyHint="next"
                    label={isDriver ? '배송원 아이디' : '세티 아이디'}
                    onChange={setUsername}
                    value={username}
                />
                <TextField
                    autoComplete="current-password"
                    enterKeyHint="go"
                    label={isDriver ? '배송원 비밀번호' : '세티 비밀번호'}
                    onChange={setPassword}
                    onEnter={() => void login()}
                    type="password"
                    value={password}
                />
                <button className={`${styles['inline-link']} type-body-medium`} onClick={() => navigate(isDriver ? routes.driverSignUp : routes.signUp)} type="button">
                    <span className={styles['inline-link__muted']}>아직 계정이 없나요? </span>
                    <span className={styles['inline-link__strong']}>계정 만들기</span>
                </button>
            </div>
        </Scaffold>
    );
}
