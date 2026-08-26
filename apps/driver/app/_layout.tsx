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
import { colors } from '@/theme';

export default function RootLayout() {
  const [loaded, error] = useFonts({
    DoHyeon_400Regular,
    NotoSansKR_400Regular,
    NotoSansKR_500Medium,
    NotoSansKR_700Bold,
    NotoSansKR_900Black,
  });

  // 폰트 로딩 실패(오프라인 등)에도 시스템 폴백으로 앱을 띄운다.
  const ready = loaded || !!error;

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      {ready ? (
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: colors.screenBg },
            animation: 'slide_from_right',
          }}
        />
      ) : (
        <View style={{ flex: 1, backgroundColor: colors.screenBg, alignItems: 'center', justifyContent: 'center' }}>
          <ActivityIndicator color={colors.brand} />
        </View>
      )}
    </SafeAreaProvider>
  );
}
