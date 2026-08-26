import { StyleSheet, View } from 'react-native';
import { DeliveryStatus } from '@/model/delivery';
import { statusMeta } from '@/lib/statusMeta';
import { radius } from '@/theme';
import { AppText } from './AppText';

interface StatusPillProps {
  status: DeliveryStatus | string;
  /** 앞에 상태색 점을 붙일지. */
  withDot?: boolean;
}

/** 상태 배지(라벨 + 색). */
export function StatusPill({ status, withDot = false }: StatusPillProps) {
  const meta = statusMeta(status);
  return (
    <View style={[styles.pill, { backgroundColor: meta.bg }]}>
      {withDot && <View style={[styles.dot, { backgroundColor: meta.color }]} />}
      <AppText variant="bold" style={[styles.label, { color: meta.color }]}>
        {meta.label}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    alignSelf: 'flex-start',
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: radius.chip,
  },
  dot: { width: 6, height: 6, borderRadius: 3 },
  label: { fontSize: 11 },
});
