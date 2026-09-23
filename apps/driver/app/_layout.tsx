import { useEffect } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { Stack, useRouter, useSegments } from 'expo-router';
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

const AUTH_ROUTES = ['login', 'signup'];

/**
 * 인증 상태에 따라 화면을 가른다(Expo Router 공식 인증 패턴).
 * 현재 위치(segments)와 status를 보고 명령형으로 리다이렉트한다.
 * - guest 인데 인증 화면 밖 → /login 으로
 * - authed 인데 인증 화면 안 → 홈 탭(/)으로
 * 렌더 트리는 항상 같은 Stack이라, status가 바뀌면 이 효과가 위치를 맞춘다.
 */
function RootNavigator() {
  const { status } = useAuth();
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (status === 'loading') return;
    const inAuthGroup = AUTH_ROUTES.includes(segments[0] as string);
    if (status === 'guest' && !inAuthGroup) {
      router.replace('/login');
    } else if (status === 'authed' && inAuthGroup) {
      router.replace('/');
    }
  }, [status, segments, router]);

  if (status === 'loading') return <Splash />;

  return (
    <Stack
      screenOptions={{
        headerShown: false,
        contentStyle: { backgroundColor: colors.screenBg },
        animation: 'slide_from_right',
      }}
    />
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
