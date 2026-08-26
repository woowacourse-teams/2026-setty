import { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, Pressable, ScrollView, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { deliveryAuthApi } from '@/api/deliveryAuthApi';
import { SignupRequest } from '@/model/auth';
import { errorMessage } from '@/lib/errorMessage';
import { AppText } from '@/components/AppText';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { TextField } from '@/components/TextField';
import { colors } from '@/theme';
import { SignupErrors, validateSignup } from './validators';
import { styles } from './SignupScreen.styles';

const EMPTY: SignupRequest = {
  loginId: '',
  password: '',
  phoneNumber: '',
  licensePlateNumber: '',
  carType: '',
  businessRegistrationNumber: '',
};

/** 배송원 회원가입. 성공하면 로그인 화면으로 되돌려 로그인을 유도한다. */
export function SignupScreen() {
  const router = useRouter();
  const [form, setForm] = useState<SignupRequest>(EMPTY);
  const [errors, setErrors] = useState<SignupErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const set = (key: keyof SignupRequest) => (value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const submit = async () => {
    const found = validateSignup(form);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setFormError(null);
      return;
    }
    setFormError(null);
    setSubmitting(true);
    try {
      await deliveryAuthApi.signup(form);
      Alert.alert('가입 완료', '이제 로그인해 주세요', [
        { text: '확인', onPress: () => router.replace('/login') },
      ]);
    } catch (e) {
      setFormError(errorMessage(e, '회원가입에 실패했어요'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Screen>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View style={styles.topBar}>
          <Pressable onPress={() => router.back()} hitSlop={10} style={styles.backBtn}>
            <Feather name="chevron-left" size={26} color={colors.ink} />
          </Pressable>
          <AppText variant="display" style={styles.title}>
            배송원 회원가입
          </AppText>
        </View>

        <ScrollView
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <TextField
            label="아이디"
            value={form.loginId}
            onChangeText={set('loginId')}
            error={errors.loginId}
            hint="영문 소문자·숫자 4~20자"
            placeholder="driver01"
            autoCapitalize="none"
            autoCorrect={false}
          />
          <TextField
            label="비밀번호"
            value={form.password}
            onChangeText={set('password')}
            error={errors.password}
            hint="8~64자"
            placeholder="비밀번호"
            secureTextEntry
            autoCapitalize="none"
          />
          <TextField
            label="전화번호"
            value={form.phoneNumber}
            onChangeText={set('phoneNumber')}
            error={errors.phoneNumber}
            hint="010-0000-0000"
            placeholder="010-0000-0000"
            keyboardType="phone-pad"
          />
          <TextField
            label="차량 번호판"
            value={form.licensePlateNumber}
            onChangeText={set('licensePlateNumber')}
            error={errors.licensePlateNumber}
            hint="00가0000"
            placeholder="12가3456"
          />
          <TextField
            label="차종"
            value={form.carType}
            onChangeText={set('carType')}
            error={errors.carType}
            hint="1~30자"
            placeholder="다마스"
          />
          <TextField
            label="사업자등록번호"
            value={form.businessRegistrationNumber}
            onChangeText={set('businessRegistrationNumber')}
            error={errors.businessRegistrationNumber}
            hint="000-00-00000"
            placeholder="123-01-56789"
            keyboardType="numbers-and-punctuation"
          />

          {formError ? (
            <AppText variant="medium" style={styles.formError}>
              {formError}
            </AppText>
          ) : null}

          <PrimaryButton
            label="가입하기"
            onPress={submit}
            loading={submitting}
            style={styles.submit}
          />
        </ScrollView>
      </KeyboardAvoidingView>
    </Screen>
  );
}
