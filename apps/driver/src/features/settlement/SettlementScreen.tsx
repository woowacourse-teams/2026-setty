import { useMemo } from 'react';
import { ActivityIndicator, FlatList, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { ShipmentSummaryResponse } from '@/model/delivery';
import { formatFee, shortAddress } from '@/lib/format';
import { AppText } from '@/components/AppText';
import { Screen } from '@/components/Screen';
import { colors } from '@/theme';
import { useShipments } from '@/features/shipments/useShipments';
import { styles } from './SettlementScreen.styles';

/**
 * 정산. 전용 API가 없어 완료(DELIVERED) 배차에서 앱이 계산한다.
 * (오늘 수입 · 완료 건수 · 건당 평균 — apps/docs/open-questions.md 참고)
 */
export function SettlementScreen() {
  const { items, loading, error } = useShipments();

  const { delivered, total, count, avg } = useMemo(() => {
    const done = items.filter((s) => s.status === 'DELIVERED');
    const sum = done.reduce((a, s) => a + s.deliveryFee, 0);
    return {
      delivered: done,
      total: sum,
      count: done.length,
      avg: done.length ? Math.round(sum / done.length) : 0,
    };
  }, [items]);

  return (
    <Screen edges={['top']}>
      <View style={styles.hero}>
        <AppText variant="display" style={styles.heroTitle}>
          정산
        </AppText>
        <AppText variant="medium" style={styles.heroCaption}>
          오늘 수입
        </AppText>
        <AppText variant="display" style={styles.heroAmount}>
          {formatFee(total)}
        </AppText>
        <View style={styles.stats}>
          <Stat label="완료 건수" value={`${count}건`} />
          <Stat label="건당 평균" value={formatFee(avg)} />
        </View>
      </View>

      <AppText variant="bold" style={styles.listHeader}>
        완료한 배송
      </AppText>

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.brand} />
        </View>
      ) : error ? (
        <View style={styles.centerFill}>
          <AppText variant="medium" style={styles.emptyText}>
            {error}
          </AppText>
        </View>
      ) : (
        <FlatList
          data={delivered}
          keyExtractor={(it) => String(it.deliveryId)}
          renderItem={({ item }: { item: ShipmentSummaryResponse }) => <DoneRow item={item} />}
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={
            <View style={styles.centerFill}>
              <AppText variant="medium" style={styles.emptyText}>
                완료한 배송이 아직 없어요
              </AppText>
            </View>
          }
        />
      )}
    </Screen>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.stat}>
      <AppText variant="medium" style={styles.statLabel}>
        {label}
      </AppText>
      <AppText variant="display" style={styles.statValue}>
        {value}
      </AppText>
    </View>
  );
}

function DoneRow({ item }: { item: ShipmentSummaryResponse }) {
  return (
    <View style={styles.row}>
      <View style={styles.rowIcon}>
        <Feather name="check" size={18} color={colors.pillGreenText} />
      </View>
      <View style={styles.rowBody}>
        <AppText variant="bold" style={styles.rowName} numberOfLines={1}>
          {item.itemName}
        </AppText>
        <AppText style={styles.rowPlace}>{shortAddress(item.deliveryAddress)}</AppText>
      </View>
      <AppText variant="display" style={styles.rowFee}>
        +{formatFee(item.deliveryFee)}
      </AppText>
    </View>
  );
}
