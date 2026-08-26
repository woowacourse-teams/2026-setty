import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { colors, radius } from '@/theme';
import { AppText } from './AppText';

interface Point {
  label: string;
  place: string;
  address?: string;
  /** 있으면 전화 걸기 버튼을 보여준다. */
  phone?: string;
  phoneLabel?: string;
}

interface RouteLineProps {
  pickup: Point;
  destination: Point;
}

/**
 * 출발지 → 목적지 세로 타임라인. 점-점선-핀 형태(디자인 route 카드).
 * phone이 있으면 전화 걸기 버튼을 함께 렌더한다.
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
        <PointBlock point={pickup} accent={colors.accent} callBg={colors.pillNeutralBg} callColor={colors.teal} />
        <PointBlock
          point={destination}
          accent={colors.pillGreenText}
          callBg={colors.pillGreenBg}
          callColor={colors.pillGreenText}
        />
      </View>
    </View>
  );
}

function PointBlock({
  point,
  accent,
  callBg,
  callColor,
}: {
  point: Point;
  accent: string;
  callBg: string;
  callColor: string;
}) {
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
      {point.phone ? (
        <Pressable
          onPress={() => Linking.openURL(`tel:${point.phone}`)}
          style={[styles.callBtn, { backgroundColor: callBg }]}
        >
          <Feather name="phone" size={14} color={callColor} />
          <AppText variant="bold" style={[styles.callText, { color: callColor }]}>
            {point.phoneLabel ?? '전화'} {point.phone}
          </AppText>
        </Pressable>
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
  callBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    alignSelf: 'flex-start',
    marginTop: 8,
    paddingVertical: 7,
    paddingHorizontal: 12,
    borderRadius: radius.md,
  },
  callText: { fontSize: 12.5 },
});
