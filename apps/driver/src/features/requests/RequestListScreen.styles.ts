import { StyleSheet } from 'react-native';
import { colors, radius, spacing } from '@/theme';

export const styles = StyleSheet.create({
  header: { paddingHorizontal: spacing.screenX, paddingTop: 8, paddingBottom: 14 },
  liveRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  liveDot: { width: 9, height: 9, borderRadius: 5, backgroundColor: colors.liveDot },
  liveText: { fontSize: 12.5, color: colors.teal, letterSpacing: 0.3 },

  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  titleLeft: { flexDirection: 'row', alignItems: 'baseline', gap: 10 },
  title: { fontSize: 25, color: colors.ink },
  countPill: {
    backgroundColor: colors.pillGreenBg,
    paddingVertical: 4,
    paddingHorizontal: 11,
    borderRadius: radius.chip,
  },
  countText: { fontSize: 13, color: colors.pillGreenText },

  refreshBtn: {
    height: 38,
    paddingHorizontal: 14,
    borderRadius: radius.md,
    backgroundColor: '#DFF6F2',
    alignItems: 'center',
    justifyContent: 'center',
  },
  refreshPressed: { backgroundColor: colors.gradientTop },
  refreshText: { fontSize: 13, color: colors.teal },

  hint: { marginTop: 12, fontSize: 11.5, color: colors.textFaint },

  listContent: {
    paddingHorizontal: spacing.screenX,
    paddingTop: 8,
    paddingBottom: 40,
    gap: 12,
    flexGrow: 1,
  },

  centerFill: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 40 },
  emptyTitle: { fontSize: 14, color: colors.textFaint, marginTop: 12 },
  emptySub: { fontSize: 12.5, color: '#A6BFC2', marginTop: 4 },
  errorText: { fontSize: 14, color: colors.dangerText, textAlign: 'center' },
  retryText: { fontSize: 13, color: colors.teal, marginTop: 10 },
});
