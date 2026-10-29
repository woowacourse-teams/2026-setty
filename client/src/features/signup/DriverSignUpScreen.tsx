import { useState } from 'react';
import { useNavigate } from 'react-router';
import { deliveryRepository } from '../../api/deliveryRepository';
import {
    BUSINESS_NUMBER_REGEX,
    CAR_TYPE_MAX_LENGTH,
    formatBusinessNumber,
    LICENSE_PLATE_REGEX
} from '../../model/delivery';
import { digitsOnly, formatPhoneNumber, isPhoneNumberValid, PHONE_NUMBER_MAX_DIGITS } from '../../model/member';
import { routes } from '../../navigation';
import { BottomBar, Button, LoadingIndicator, Scaffold, TextField } from '../../ui/components';
import { showSnackbar } from '../../ui/snackbar';
import { AccountRoleToggle } from '../login/AccountRoleToggle';
import styles from '../screens.module.css';
import {
    LOGIN_ID_REGEX,
    PASSWORD_MAX_LENGTH,
    PASSWORD_MIN_LENGTH,
    signUpErrorMessage,
    SignUpSection
} from './SignUpScreen';

type DriverSignUpForm = {
    loginId: string;
    password: string;
    passwordConfirm: string;
    // 하이픈 없는 숫자만 저장한다
    phoneNumber: string;
    licensePlateNumber: string;
    carType: string;
    businessRegistrationNumber: string;
};

function fieldErrors(form: DriverSignUpForm) {
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
        licensePlateNumber: form.licensePlateNumber && !LICENSE_PLATE_REGEX.test(form.licensePlateNumber)
            ? '차량 번호판은 00가0000 형식입니다'
            : null,
        carType: form.carType.length > CAR_TYPE_MAX_LENGTH ? `차종은 ${CAR_TYPE_MAX_LENGTH}자 이내입니다` : null,
        businessRegistrationNumber: form.businessRegistrationNumber && !BUSINESS_NUMBER_REGEX.test(form.businessRegistrationNumber)
            ? '사업자등록번호는 000-00-00000 형식입니다'
            : null
    };
}

export function DriverSignUpScreen() {
    const navigate = useNavigate();
    const [form, setForm] = useState<DriverSignUpForm>({
        loginId: '',
        password: '',
        passwordConfirm: '',
        phoneNumber: '',
        licensePlateNumber: '',
        carType: '',
        businessRegistrationNumber: ''
    });
    const [isLoading, setIsLoading] = useState(false);
    const errors = fieldErrors(form);
    const canSubmit = !isLoading
        && Object.values(form).every((value) => value.trim().length > 0)
        && Object.values(errors).every((error) => error === null);

    const update = (patch: Partial<DriverSignUpForm>) => setForm((current) => ({ ...current, ...patch }));

    const signUp = async () => {
        if (!canSubmit) return;
        setIsLoading(true);
        try {
            await deliveryRepository.signUp({
                loginId: form.loginId,
                password: form.password,
                phoneNumber: formatPhoneNumber(form.phoneNumber),
                licensePlateNumber: form.licensePlateNumber,
                carType: form.carType.trim(),
                businessRegistrationNumber: form.businessRegistrationNumber
            });
            navigate(routes.driverLogin, { replace: true });
            showSnackbar('배송원 가입이 완료되었습니다.', { keepOnNextScreen: true });
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
                <AccountRoleToggle path={routes.signUp} role="driver" />
                <h1 className="type-headline-large-em" style={{ margin: 0 }}>SETTY 배송원 가입</h1>
                <p className={`${styles.lead} type-body-large-em`}>{'배송원 계정을 만들고\n가까운 가구 배송을 시작하세요.'}</p>
                <SignUpSection title="계정 정보">
                    <TextField
                        autoComplete="username"
                        enterKeyHint="next"
                        isError={errors.loginId !== null}
                        label="배송원 아이디"
                        onChange={(loginId) => update({ loginId })}
                        supportingText={errors.loginId ?? undefined}
                        value={form.loginId}
                    />
                    <TextField
                        autoComplete="new-password"
                        enterKeyHint="next"
                        isError={errors.password !== null}
                        label="배송원 비밀번호"
                        onChange={(password) => update({ password })}
                        supportingText={errors.password ?? undefined}
                        type="password"
                        value={form.password}
                    />
                    <TextField
                        autoComplete="new-password"
                        enterKeyHint="next"
                        isError={errors.passwordConfirm !== null}
                        label="배송원 비밀번호 확인"
                        onChange={(passwordConfirm) => update({ passwordConfirm })}
                        supportingText={errors.passwordConfirm ?? undefined}
                        type="password"
                        value={form.passwordConfirm}
                    />
                    <div style={{ height: 16 }} />
                </SignUpSection>
                <SignUpSection title="배송 정보">
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
                    <TextField
                        enterKeyHint="next"
                        isError={errors.licensePlateNumber !== null}
                        label="차량 번호판"
                        onChange={(value) => update({ licensePlateNumber: value.replace(/\s/g, '') })}
                        supportingText={errors.licensePlateNumber ?? '예: 12가3456'}
                        value={form.licensePlateNumber}
                    />
                    <TextField
                        enterKeyHint="next"
                        isError={errors.carType !== null}
                        label="차종"
                        onChange={(carType) => update({ carType })}
                        supportingText={errors.carType ?? '예: 1톤 탑차'}
                        value={form.carType}
                    />
                    <TextField
                        enterKeyHint="go"
                        inputMode="numeric"
                        isError={errors.businessRegistrationNumber !== null}
                        label="사업자등록번호"
                        onChange={(value) => update({ businessRegistrationNumber: formatBusinessNumber(value) })}
                        onEnter={() => void signUp()}
                        supportingText={errors.businessRegistrationNumber ?? undefined}
                        value={form.businessRegistrationNumber}
                    />
                </SignUpSection>
            </div>
        </Scaffold>
    );
}
