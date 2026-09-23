import { StyleSheet } from 'react-native';
import { colors, radius, spacing } from '@/theme';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.cardBg,
    borderRadius: radius.card,
    padding: spacing.cardPad,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  pressed: { opacity: 0.9 },

  topRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 10,
  },
  left: { flex: 1, minWidth: 0 },
  categoryPill: {
    alignSelf: 'flex-start',
    backgroundColor: colors.pillNeutralBg,
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: radius.chip,
  },
  categoryText: { fontSize: 11, color: colors.pillNeutralText },
  itemName: { fontSize: 18, color: colors.ink, marginTop: 9 },

  feeCol: { alignItems: 'flex-end' },
  feeLabel: { fontSize: 11, color: colors.textFaint2 },
  feeValue: { fontSize: 19, color: colors.teal, marginTop: 2 },

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
  routeDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.accent,
  },
  routeText: { flex: 1, fontSize: 12.5, color: colors.textMuted },

  acceptBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: colors.brand,
    paddingVertical: 8,
    paddingHorizontal: 13,
    borderRadius: radius.md,
  },
  acceptBtnPressed: { backgroundColor: colors.brandPressed },
  acceptText: { fontSize: 12, color: colors.white },
});
