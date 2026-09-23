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

  content: { paddingHorizontal: spacing.screenX, paddingTop: 6, paddingBottom: 24, gap: 0 },

  liveRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  liveDot: { width: 9, height: 9, borderRadius: 5, backgroundColor: colors.danger },
  liveText: { fontSize: 13, color: colors.teal, letterSpacing: 0.5 },

  itemName: { fontSize: 27, color: colors.ink, marginTop: 12, lineHeight: 34 },
  categoryPill: {
    alignSelf: 'flex-start',
    marginTop: 10,
    backgroundColor: colors.pillNeutralBg,
    paddingVertical: 5,
    paddingHorizontal: 12,
    borderRadius: radius.chip,
  },
  categoryText: { fontSize: 12, color: colors.pillNeutralText },

  feeCard: {
    marginTop: 20,
    backgroundColor: colors.brand,
    borderRadius: radius.xxl,
    paddingVertical: 20,
    paddingHorizontal: 22,
  },
  feeLabel: { fontSize: 12, color: colors.textOnDarkSub },
  feeValue: { fontSize: 38, color: colors.white, marginTop: 2 },

  routeCard: {
    marginTop: 16,
    backgroundColor: colors.cardBg,
    borderRadius: radius.xxl,
    padding: 18,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },

  footer: {
    flexDirection: 'row',
    gap: 10,
    paddingHorizontal: spacing.screenX,
    paddingTop: 12,
    paddingBottom: 12,
    backgroundColor: colors.screenBg,
  },

  centerFill: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 40 },
  errorText: { fontSize: 14, color: colors.dangerText, textAlign: 'center' },
});
