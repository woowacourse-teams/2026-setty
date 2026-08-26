import { StyleSheet, View } from 'react-native';
import { colors } from '@/theme';
import { AppText } from './AppText';

interface Point {
  label: string;
  place: string;
  address?: string;
}

interface RouteLineProps {
  pickup: Point;
  destination: Point;
}

/**
 * 출발지 → 목적지 세로 타임라인. 점-점선-핀 형태(디자인 route 카드).
 */
export function RouteLine({ pickup, destination }: RouteLineProps) {
  return (
    <View style={styles.row}>
      <View style={styles.rail}>
        <View style={styles.startDot} />
        <View style={styles.dash} />
        <View style={styles.endPin} />
      </View>
      <View style={styles.body}>
        <PointBlock point={pickup} accent={colors.accent} />
        <PointBlock point={destination} accent={colors.pillGreenText} />
      </View>
    </View>
  );
}

function PointBlock({ point, accent }: { point: Point; accent: string }) {
  return (
    <View style={styles.pointBlock}>
      <AppText variant="bold" style={[styles.pointLabel, { color: accent }]}>
        {point.label}
      </AppText>
      <AppText variant="bold" style={styles.pointPlace}>
        {point.place}
      </AppText>
      {point.address ? (
        <AppText style={styles.pointAddress}>{point.address}</AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 14 },
  rail: { alignItems: 'center', paddingTop: 5 },
  startDot: {
    width: 13,
    height: 13,
    borderRadius: 7,
    backgroundColor: colors.accent,
    borderWidth: 3,
    borderColor: colors.accentSoft,
  },
  dash: {
    width: 2,
    flex: 1,
    minHeight: 40,
    marginVertical: 4,
    backgroundColor: colors.divider,
  },
  endPin: {
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: colors.brand,
  },
  body: { flex: 1, gap: 18 },
  pointBlock: { gap: 3 },
  pointLabel: { fontSize: 11 },
  pointPlace: { fontSize: 15, color: colors.ink },
  pointAddress: { fontSize: 13, color: colors.textMuted, lineHeight: 19 },
});
