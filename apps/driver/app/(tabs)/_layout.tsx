import { Tabs } from 'expo-router';
import { BottomTabBar } from '@/components/BottomTabBar';

/** 하단 탭: 요청 / 내 배차 / 정산. */
export default function TabsLayout() {
  return (
    <Tabs tabBar={(props) => <BottomTabBar {...props} />} screenOptions={{ headerShown: false }}>
      <Tabs.Screen name="index" options={{ title: '요청' }} />
      <Tabs.Screen name="shipments" options={{ title: '내 배차' }} />
      <Tabs.Screen name="settlement" options={{ title: '정산' }} />
    </Tabs>
  );
}
