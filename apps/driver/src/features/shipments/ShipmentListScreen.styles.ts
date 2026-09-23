import { StyleSheet } from 'react-native';
import { colors, radius, spacing } from '@/theme';

export const styles = StyleSheet.create({
  header: { paddingHorizontal: spacing.screenX, paddingTop: 8 },
  title: { fontSize: 25, color: colors.ink },

  tabs: {
    flexDirection: 'row',
    backgroundColor: '#E3F4F1',
    borderRadius: radius.lg,
    padding: 4,
    marginTop: 16,
  },
  tab: { flex: 1, paddingVertical: 10, borderRadius: radius.sm, alignItems: 'center' },
  tabOn: { backgroundColor: colors.white },
  tabText: { fontSize: 15 },
  tabTextOn: { color: colors.ink },
  tabTextOff: { color: '#7C9A9E' },

  listContent: {
    paddingHorizontal: spacing.screenX,
    paddingTop: 16,
    paddingBottom: 24,
    gap: 12,
    flexGrow: 1,
  },
  centerFill: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 40 },
  emptyText: { fontSize: 14, color: colors.textFaint, marginTop: 12 },
  errorText: { fontSize: 14, color: colors.dangerText, textAlign: 'center' },
});
