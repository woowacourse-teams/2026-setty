import { useCallback, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, View } from 'react-native';
import { useRouter } from 'expo-router';
import { ShipmentSummaryResponse } from '@/model/delivery';
import { AppText } from '@/components/AppText';
import { Screen } from '@/components/Screen';
import { colors } from '@/theme';
import { useShipments } from './useShipments';
import { ShipmentCard } from './ShipmentCard';
import { styles } from './ShipmentListScreen.styles';

type Tab = 'active' | 'done';

/** 내 배차: 진행중 / 완료 목록. */
export function ShipmentListScreen() {
  const router = useRouter();
  const { items, loading, refreshing, error, refresh } = useShipments();
  const [tab, setTab] = useState<Tab>('active');

  const openDetail = useCallback(
    (deliveryId: number) =>
      router.push({
        pathname: '/shipment/[deliveryId]',
        params: { deliveryId: String(deliveryId) },
      }),
    [router],
  );

  const list = useMemo(
    () =>
      items.filter((s) => (tab === 'active' ? s.status !== 'DELIVERED' : s.status === 'DELIVERED')),
    [items, tab],
  );

  return (
    <Screen edges={['top']}>
      <View style={styles.header}>
        <AppText variant="display" style={styles.title}>
          내 배차
        </AppText>
        <View style={styles.tabs}>
          <TabButton label="진행중" active={tab === 'active'} onPress={() => setTab('active')} />
          <TabButton label="완료" active={tab === 'done'} onPress={() => setTab('done')} />
        </View>
      </View>

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.brand} />
        </View>
      ) : error ? (
        <View style={styles.centerFill}>
          <AppText variant="medium" style={styles.errorText}>
            {error}
          </AppText>
        </View>
      ) : (
        <FlatList
          data={list}
          keyExtractor={(it) => String(it.deliveryId)}
          renderItem={({ item }: { item: ShipmentSummaryResponse }) => (
            <ShipmentCard shipment={item} onPress={() => openDetail(item.deliveryId)} />
          )}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={colors.brand} />
          }
          ListEmptyComponent={
            <View style={styles.centerFill}>
              <AppText variant="medium" style={styles.emptyText}>
                {tab === 'active' ? '진행중인 배차가 없어요' : '완료한 배차가 없어요'}
              </AppText>
            </View>
          }
        />
      )}
    </Screen>
  );
}

function TabButton({
  label,
  active,
  onPress,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={[styles.tab, active && styles.tabOn]}>
      <AppText
        variant="display"
        style={[styles.tabText, active ? styles.tabTextOn : styles.tabTextOff]}
      >
        {label}
      </AppText>
    </Pressable>
  );
}
