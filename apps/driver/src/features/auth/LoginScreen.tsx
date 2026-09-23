import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, View } from 'react-native';
import { useRouter } from 'expo-router';
import { deliveryAuthApi } from '@/api/deliveryAuthApi';
import { errorMessage } from '@/lib/errorMessage';
import { AppText } from '@/components/AppText';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { TextField } from '@/components/TextField';
import { useAuth } from './AuthContext';
import { validateLogin } from './validators';
import { styles } from './LoginScreen.styles';

/** 배송원 로그인. 성공 시 토큰 저장 → 루트 게이트가 배차 화면으로 전환한다. */
export function LoginScreen() {
  const router = useRouter();
  const { signIn } = useAuth();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    const invalid = validateLogin(loginId, password);
    if (invalid) {
      setError(invalid);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const { token } = await deliveryAuthApi.login({ loginId: loginId.trim(), password });
      await signIn(token);
      // 루트 게이트가 인증 상태를 감지해 이동하지만, 성공 즉시 명시적으로도 이동한다.
      router.replace('/');
    } catch (e) {
      console.warn('[login] failed', e);
      setError(errorMessage(e, '로그인에 실패했어요'));
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
        <ScrollView
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.hero}>
            <AppText variant="display" style={styles.brand}>
              SETTY 배송원
            </AppText>
            <AppText variant="medium" style={styles.subtitle}>
              로그인하고 배차를 시작하세요
            </AppText>
          </View>

          <View style={styles.form}>
            <TextField
              label="아이디"
              value={loginId}
              onChangeText={setLoginId}
              placeholder="영문 소문자·숫자 4~20자"
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="username"
              returnKeyType="next"
            />
            <TextField
              label="비밀번호"
              value={password}
              onChangeText={setPassword}
              placeholder="비밀번호"
              secureTextEntry
              autoCapitalize="none"
              autoComplete="password"
              returnKeyType="done"
              onSubmitEditing={submit}
            />

            {error ? (
              <AppText variant="medium" style={styles.error}>
                {error}
              </AppText>
            ) : null}

            <PrimaryButton
              label="로그인"
              onPress={submit}
              loading={submitting}
              style={styles.submit}
            />
          </View>

          <View style={styles.footer}>
            <AppText variant="medium" style={styles.footerText}>
              아직 계정이 없나요?
            </AppText>
            <AppText
              variant="bold"
              style={styles.footerLink}
              onPress={() => router.push('/signup')}
            >
              회원가입
            </AppText>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </Screen>
  );
}
