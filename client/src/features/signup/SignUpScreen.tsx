import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { ApiError, NetworkError } from '../../api/http';
import { userRepository } from '../../api/repositories';
import {
    ADDRESS_MAX_LENGTH,
    digitsOnly,
    formatPhoneNumber,
    isPhoneNumberValid,
    PHONE_NUMBER_MAX_DIGITS
} from '../../model/member';
import { routes } from '../../navigation';
import { BottomBar, Button, LoadingIndicator, Scaffold, TextField } from '../../ui/components';
import { showSnackbar } from '../../ui/snackbar';
import { DongField, useDongCatalog } from '../dong/DongField';
import styles from '../screens.module.css';

const LOGIN_ID_REGEX = /^[a-z0-9]{4,20}$/;
const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 64;

type SignUpForm = {
    loginId: string;
    password: string;
    passwordConfirm: string;
    // 하이픈 없는 숫자만 저장한다
    phoneNumber: string;
    address: string;
    dongCode: string | null;
};

function fieldErrors(form: SignUpForm) {
    return {
        loginId: form.loginId && !LOGIN_ID_REGEX.test(form.loginId)
            ? '아이디는 영문 소문자,숫자로 구성되며 4~20자입니다'
            : null,
        password: form.password && (form.password.length < PASSWORD_MIN_LENGTH || form.password.length > PASSWORD_MAX_LENGTH)
            ? '비밀번호는 8~64자입니다'
            : null,
        passwordConfirm: form.passwordConfirm && form.passwordConfirm !== form.password
            ? '비밀번호가 일치하지 않습니다'
            : null,
        phoneNumber: form.phoneNumber && !isPhoneNumberValid(form.phoneNumber)
            ? '전화번호는 010-0000-0000 형식입니다'
            : null,
        address: form.address.length > ADDRESS_MAX_LENGTH ? `주소는 ${ADDRESS_MAX_LENGTH}자 이내입니다` : null
    };
}

function signUpErrorMessage(error: unknown): string {
    if (error instanceof ApiError) {
        switch (error.code) {
            case 'DUPLICATE_LOGIN_ID':
                return '이미 사용 중인 아이디입니다';
            case 'INVALID_REQUEST':
                return '입력한 정보를 다시 확인해주세요';
            case 'DELIVERY_AREA_NOT_SUPPORTED':
                return '배송 가능 지역이 아닙니다. 행정동을 다시 골라주세요';
            default:
                return '알 수 없는 에러가 발생했습니다';
        }
    }
    if (error instanceof NetworkError) return '네트워크 에러가 발생했습니다';
    return '알 수 없는 에러가 발생했습니다';
}

export function SignUpScreen() {
    const navigate = useNavigate();
    const { catalog, reload } = useDongCatalog();
    const [form, setForm] = useState<SignUpForm>({
        loginId: '',
        password: '',
        passwordConfirm: '',
        phoneNumber: '',
        address: '',
        dongCode: null
    });
    const [isLoading, setIsLoading] = useState(false);
    const errors = fieldErrors(form);
    const canSubmit = !isLoading
        && [form.loginId, form.password, form.passwordConfirm, form.phoneNumber].every(Boolean)
        && form.address.trim().length > 0
        && form.dongCode !== null
        && Object.values(errors).every((error) => error === null);

    const update = (patch: Partial<SignUpForm>) => setForm((current) => ({ ...current, ...patch }));

    const signUp = async () => {
        if (!canSubmit || form.dongCode === null) return;
        setIsLoading(true);
        try {
            await userRepository.signUp({
                loginId: form.loginId,
                password: form.password,
                phoneNumber: formatPhoneNumber(form.phoneNumber),
                address: form.address.trim(),
                dongCode: form.dongCode
            });
            // 로그인 화면으로 돌아가 가입 완료를 알린다
            const historyIndex = (window.history.state as { idx?: number } | null)?.idx ?? 0;
            if (historyIndex > 0) {
                navigate(-1);
            } else {
                navigate(routes.login, { replace: true });
            }
            showSnackbar('회원가입이 완료되었습니다.', { keepOnNextScreen: true });
        } catch (error) {
            setIsLoading(false);
            showSnackbar(signUpErrorMessage(error));
        }
    };

    return (
        <Scaffold
            bottomBar={(
                <BottomBar wide>
                    {isLoading ? <LoadingIndicator /> : (
                        <Button disabled={!canSubmit} fullWidth onClick={() => void signUp()} size="medium">가입하기</Button>
                    )}
                </BottomBar>
            )}
        >
            <div className={styles['form-column']}>
                <h1 className="type-headline-large-em" style={{ margin: 0 }}>SETTY 회원가입</h1>
                <p className={`${styles.lead} type-body-large-em`}>{'계정을 만들고\nSETTY에서 중고 가구 거래를 시작하세요.'}</p>
                <SignUpSection title="계정 정보">
                    <TextField
                        autoComplete="username"
                        enterKeyHint="next"
                        isError={errors.loginId !== null}
                        label="세티 아이디"
                        onChange={(loginId) => update({ loginId })}
                        supportingText={errors.loginId ?? undefined}
                        value={form.loginId}
                    />
                    <TextField
                        autoComplete="new-password"
                        enterKeyHint="next"
                        isError={errors.password !== null}
                        label="세티 비밀번호"
                        onChange={(password) => update({ password })}
                        supportingText={errors.password ?? undefined}
                        type="password"
                        value={form.password}
                    />
                    <TextField
                        autoComplete="new-password"
                        enterKeyHint="next"
                        isError={errors.passwordConfirm !== null}
                        label="세티 비밀번호 확인"
                        onChange={(passwordConfirm) => update({ passwordConfirm })}
                        supportingText={errors.passwordConfirm ?? undefined}
                        type="password"
                        value={form.passwordConfirm}
                    />
                    <div style={{ height: 16 }} />
                </SignUpSection>
                <SignUpSection title="거래 정보">
                    <TextField
                        autoComplete="tel"
                        enterKeyHint="next"
                        inputMode="tel"
                        isError={errors.phoneNumber !== null}
                        label="전화번호"
                        onChange={(value) => update({ phoneNumber: digitsOnly(value, PHONE_NUMBER_MAX_DIGITS) })}
                        supportingText={errors.phoneNumber ?? undefined}
                        type="tel"
                        value={formatPhoneNumber(form.phoneNumber)}
                    />
                    <DongField
                        catalog={catalog}
                        disabled={isLoading}
                        onDongSelected={(dong) => update({ dongCode: dong.code })}
                        onRetry={reload}
                        selectedCode={form.dongCode}
                    />
                    <TextField
                        autoComplete="street-address"
                        enterKeyHint="go"
                        isError={errors.address !== null}
                        label="주소"
                        onChange={(address) => update({ address })}
                        onEnter={() => void signUp()}
                        supportingText={errors.address ?? undefined}
                        value={form.address}
                    />
                </SignUpSection>
            </div>
        </Scaffold>
    );
}

function SignUpSection({ title, children }: { title: string; children: ReactNode }) {
    return (
        <section className={styles['form-section']}>
            <h2 className={`${styles['form-section__title']} type-title-medium-em`}>{title}</h2>
            {children}
        </section>
    );
}
