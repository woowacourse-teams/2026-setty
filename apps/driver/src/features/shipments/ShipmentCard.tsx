import { Pressable, StyleSheet, View } from 'react-native';
import { ShipmentSummaryResponse } from '@/model/delivery';
import { formatFee, routeText } from '@/lib/format';
import { statusMeta } from '@/lib/statusMeta';
import { AppText } from '@/components/AppText';
import { StatusPill } from '@/components/StatusPill';
import { colors, radius, spacing } from '@/theme';

/** 내 배차 목록의 카드 1개. 탭하면 상세로 이동한다. */
export function ShipmentCard({
  shipment,
  onPress,
}: {
  shipment: ShipmentSummaryResponse;
  onPress: () => void;
}) {
  const done = shipment.status === 'DELIVERED';
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [styles.card, done && styles.cardDone, pressed && styles.pressed]}
    >
      <View style={styles.topRow}>
        <View style={styles.left}>
          <StatusPill status={shipment.status} withDot />
          <AppText variant="display" style={styles.itemName} numberOfLines={1}>
            {shipment.itemName}
          </AppText>
        </View>
        <View style={styles.feeCol}>
          <AppText variant="medium" style={styles.feeLabel}>
            배송비
          </AppText>
          <AppText variant="display" style={styles.feeValue}>
            {formatFee(shipment.deliveryFee)}
          </AppText>
        </View>
      </View>
      <View style={styles.footer}>
        <View style={[styles.routeDot, { backgroundColor: statusMeta(shipment.status).color }]} />
        <AppText variant="medium" style={styles.routeText} numberOfLines={1}>
          {routeText(shipment.pickupAddress, shipment.deliveryAddress)}
        </AppText>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.cardBg,
    borderRadius: radius.card,
    padding: spacing.cardPad,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  cardDone: { backgroundColor: colors.cardBgDone, opacity: 0.92 },
  pressed: { opacity: 0.9 },
  topRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 10,
  },
  left: { flex: 1, minWidth: 0 },
  itemName: { fontSize: 18, color: colors.ink, marginTop: 9 },
  feeCol: { alignItems: 'flex-end' },
  feeLabel: { fontSize: 11, color: colors.textFaint2 },
  feeValue: { fontSize: 18, color: colors.teal, marginTop: 2 },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderStyle: 'dashed',
    borderColor: colors.divider,
  },
  routeDot: { width: 8, height: 8, borderRadius: 4 },
  routeText: { flex: 1, fontSize: 12.5, color: colors.textMuted },
});
