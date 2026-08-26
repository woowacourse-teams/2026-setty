import { ActivityIndicator, View } from 'react-native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useFonts, DoHyeon_400Regular } from '@expo-google-fonts/do-hyeon';
import {
  NotoSansKR_400Regular,
  NotoSansKR_500Medium,
  NotoSansKR_700Bold,
  NotoSansKR_900Black,
} from '@expo-google-fonts/noto-sans-kr';
import { AuthProvider, useAuth } from '@/features/auth/AuthContext';
import { colors } from '@/theme';

function Splash() {
  return (
    <View style={styles.splash}>
      <ActivityIndicator color={colors.brand} />
    </View>
  );
}

/**
 * 인증 상태에 따라 접근 가능한 화면을 가른다(Stack.Protected).
 * - authed: 탭 + 상세 화면만 접근, 로그인/가입은 자동 제외 → (tabs)로 리다이렉트
 * - guest: 로그인/가입만 접근 → login으로 리다이렉트
 */
function RootNavigator() {
  const { status } = useAuth();

  if (status === 'loading') return <Splash />;

  const authed = status === 'authed';
  return (
    <Stack
      screenOptions={{
        headerShown: false,
        contentStyle: { backgroundColor: colors.screenBg },
        animation: 'slide_from_right',
      }}
    >
      <Stack.Protected guard={authed}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="request/[deliveryId]" />
        <Stack.Screen name="shipment/[deliveryId]" />
      </Stack.Protected>
      <Stack.Protected guard={!authed}>
        <Stack.Screen name="login" />
        <Stack.Screen name="signup" />
      </Stack.Protected>
    </Stack>
  );
}

export default function RootLayout() {
  const [loaded, error] = useFonts({
    DoHyeon_400Regular,
    NotoSansKR_400Regular,
    NotoSansKR_500Medium,
    NotoSansKR_700Bold,
    NotoSansKR_900Black,
  });

  // 폰트 로딩 실패(오프라인 등)에도 시스템 폴백으로 앱을 띄운다.
  const fontsReady = loaded || !!error;

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <AuthProvider>{fontsReady ? <RootNavigator /> : <Splash />}</AuthProvider>
    </SafeAreaProvider>
  );
}

const styles = {
  splash: {
    flex: 1,
    backgroundColor: colors.screenBg,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
  },
};
