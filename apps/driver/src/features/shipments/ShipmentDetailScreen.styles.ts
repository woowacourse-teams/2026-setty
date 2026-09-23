import { StyleSheet } from 'react-native';
import { colors, radius, spacing } from '@/theme';

export const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: spacing.screenX,
    paddingTop: 8,
    paddingBottom: 8,
  },
  backBtn: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    backgroundColor: '#E3F4F1',
    alignItems: 'center',
    justifyContent: 'center',
  },
  backPressed: { backgroundColor: '#D0EEEA' },
  backArrow: { fontSize: 22, color: colors.brand, lineHeight: 24 },
  headerLabel: { fontSize: 12, color: colors.textFaint2 },
  headerTitle: { fontSize: 16, color: colors.ink },

  content: { paddingHorizontal: spacing.screenX, paddingTop: 6, paddingBottom: 24 },

  titleRow: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10 },
  categoryPill: {
    alignSelf: 'flex-start',
    backgroundColor: colors.pillNeutralBg,
    paddingVertical: 5,
    paddingHorizontal: 12,
    borderRadius: radius.chip,
  },
  categoryText: { fontSize: 12, color: colors.pillNeutralText },
  itemName: { fontSize: 24, color: colors.ink, marginTop: 10, lineHeight: 31 },

  // stepper
  stepper: { flexDirection: 'row', alignItems: 'flex-start', marginTop: 22 },
  step: { alignItems: 'center', width: 60, gap: 5 },
  stepCircle: { width: 30, height: 30, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  stepNum: { fontSize: 14 },
  stepLabel: { fontSize: 11 },
  stepTime: { fontSize: 10, color: colors.textFaint, height: 12 },
  stepLine: { flex: 1, height: 3, borderRadius: 3, marginTop: 14 },

  feeCard: {
    backgroundColor: colors.brand,
    borderRadius: radius.card,
    paddingVertical: 16,
    paddingHorizontal: 20,
    marginTop: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  feeLabel: { fontSize: 13, color: colors.textOnDarkSub },
  feeValue: { fontSize: 26, color: colors.white },

  routeCard: {
    backgroundColor: colors.cardBg,
    borderRadius: radius.card,
    padding: 18,
    marginTop: 14,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },

  footer: {
    paddingHorizontal: spacing.screenX,
    paddingTop: 12,
    paddingBottom: 12,
    backgroundColor: colors.screenBg,
  },

  centerFill: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 40 },
  errorText: { fontSize: 14, color: colors.dangerText, textAlign: 'center' },
});
