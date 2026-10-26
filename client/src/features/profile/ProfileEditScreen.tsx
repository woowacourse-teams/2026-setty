import { useEffect, useState } from 'react';
import { isUnauthorized, toUserMessage } from '../../api/errors';
import { userRepository } from '../../api/repositories';
import {
    ADDRESS_MAX_LENGTH,
    digitsOnly,
    formatPhoneNumber,
    isPhoneNumberValid,
    PHONE_NUMBER_MAX_DIGITS
} from '../../model/member';
import { useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
    BackIconButton,
    BottomBar,
    Button,
    ErrorContent,
    FlexibleTopAppBar,
    LoadingContent,
    LoadingIndicator,
    Scaffold,
    SectionTitle,
    TextField
} from '../../ui/components';
import { showSnackbar } from '../../ui/snackbar';
import { DongField, useDongCatalog } from '../dong/DongField';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';

type ProfileForm = {
    loginId: string;
    // 하이픈 없는 숫자만 저장한다
    phoneNumber: string;
    address: string;
    dongCode: string | null;
    // 서버에 저장된 행정동. 바뀌었을 때만 행정동 변경을 요청한다
    savedDongCode: string | null;
};

export function ProfileEditScreen() {
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const { state, load } = useLoad(userRepository.getMe);
    const { catalog, reload } = useDongCatalog();
    const [form, setForm] = useState<ProfileForm | null>(null);
    const [isSaving, setIsSaving] = useState(false);

    // 서버의 현재 회원 정보로 처음 한 번만 입력 폼을 채운다
    useEffect(() => {
        if (state.status !== 'success' || form) return;
        const member = state.data;
        setForm({
            loginId: member.loginId,
            phoneNumber: member.phoneNumber.replace(/\D/g, ''),
            address: member.address,
            dongCode: member.dongCode,
            savedDongCode: member.dongCode
        });
    }, [state, form]);

    const isPhoneNumberError = form !== null && form.phoneNumber.length > 0 && !isPhoneNumberValid(form.phoneNumber);
    const isAddressTooLong = form !== null && form.address.length > ADDRESS_MAX_LENGTH;
    const canSubmit = form !== null
        && isPhoneNumberValid(form.phoneNumber)
        && form.address.trim().length > 0
        && !isAddressTooLong
        && form.dongCode !== null;

    const update = (patch: Partial<ProfileForm>) => setForm((current) => current && { ...current, ...patch });

    const save = async () => {
        if (!form || !canSubmit || isSaving) return;
        setIsSaving(true);
        try {
            await userRepository.updateMe(formatPhoneNumber(form.phoneNumber), form.address.trim());
            if (form.dongCode !== form.savedDongCode && form.dongCode !== null) {
                await userRepository.updateDong(form.dongCode);
                update({ savedDongCode: form.dongCode });
            }
            setIsSaving(false);
            navigateBack();
        } catch (error) {
            setIsSaving(false);
            if (isUnauthorized(error)) {
                navigateToLogin();
            } else {
                showSnackbar(toUserMessage(error));
            }
        }
    };

    return (
        <Scaffold
            bottomBar={form && (
                <BottomBar wide>
                    {isSaving ? <LoadingIndicator /> : (
                        <Button disabled={!canSubmit} fullWidth onClick={() => void save()} size="medium">저장하기</Button>
                    )}
                </BottomBar>
            )}
            topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} size="medium" title="정보 수정" />}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {form && (
                <div className={`${screenStyles['form-column']} ${screenStyles['form-column--compact']}`}>
                    <p className={`${screenStyles.lead} type-body-large-em`}>
                        {'배송에 쓰일 연락처와 주소, 행정동을 최신으로 유지해 주세요.\n이미 생성된 주문의 배송 정보는 바뀌지 않아요.'}
                    </p>
                    <SectionTitle style={{ marginTop: 12 }}>계정</SectionTitle>
                    <TextField disabled label="세티 아이디" readOnly value={form.loginId} />
                    <SectionTitle style={{ marginTop: 12 }}>거래 정보</SectionTitle>
                    <TextField
                        autoComplete="tel"
                        enterKeyHint="next"
                        inputMode="tel"
                        isError={isPhoneNumberError}
                        label="전화번호"
                        onChange={(value) => update({ phoneNumber: digitsOnly(value, PHONE_NUMBER_MAX_DIGITS) })}
                        supportingText={isPhoneNumberError ? '전화번호는 010-0000-0000 형식입니다' : undefined}
                        type="tel"
                        value={formatPhoneNumber(form.phoneNumber)}
                    />
                    <DongField
                        catalog={catalog}
                        disabled={isSaving}
                        onDongSelected={(dong) => update({ dongCode: dong.code })}
                        onRetry={reload}
                        selectedCode={form.dongCode}
                    />
                    <TextField
                        autoComplete="street-address"
                        enterKeyHint="done"
                        isError={isAddressTooLong}
                        label="주소"
                        onChange={(address) => update({ address })}
                        onEnter={() => void save()}
                        supportingText={isAddressTooLong ? `주소는 ${ADDRESS_MAX_LENGTH}자 이내입니다` : undefined}
                        value={form.address}
                    />
                </div>
            )}
        </Scaffold>
    );
}
