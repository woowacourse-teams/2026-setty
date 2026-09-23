import { StyleSheet } from 'react-native';
import { colors, radius, spacing } from '@/theme';

export const styles = StyleSheet.create({
  hero: {
    backgroundColor: colors.brand,
    borderBottomLeftRadius: 28,
    borderBottomRightRadius: 28,
    paddingHorizontal: spacing.screenX,
    paddingTop: 8,
    paddingBottom: 20,
  },
  heroTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  heroTitle: { fontSize: 22, color: colors.white },
  logoutBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: radius.chip,
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  logoutText: { fontSize: 12, color: colors.textOnDarkSub },
  heroCaption: { fontSize: 12.5, color: colors.textOnDarkSub, marginTop: 14 },
  heroAmount: { fontSize: 44, color: colors.white, marginTop: 2 },

  stats: { flexDirection: 'row', gap: 10, marginTop: 16 },
  stat: {
    flex: 1,
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderRadius: radius.lg,
    paddingVertical: 12,
    paddingHorizontal: 14,
  },
  statLabel: { fontSize: 11, color: colors.textOnDarkSub },
  statValue: { fontSize: 20, color: colors.white, marginTop: 2 },

  listHeader: {
    fontSize: 13,
    color: colors.ink,
    paddingHorizontal: spacing.screenX,
    paddingTop: 18,
    paddingBottom: 12,
  },
  listContent: { paddingHorizontal: spacing.screenX, paddingBottom: 24, gap: 10, flexGrow: 1 },

  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.cardBg,
    borderRadius: radius.lg,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  rowIcon: {
    width: 38,
    height: 38,
    borderRadius: radius.md,
    backgroundColor: colors.pillGreenBg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowBody: { flex: 1, minWidth: 0 },
  rowName: { fontSize: 14, color: colors.ink },
  rowPlace: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  rowFee: { fontSize: 17, color: colors.teal },

  centerFill: { alignItems: 'center', justifyContent: 'center', paddingVertical: 60 },
  emptyText: { fontSize: 14, color: colors.textFaint },
});
