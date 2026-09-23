import { StyleSheet } from 'react-native';
import { colors, spacing } from '@/theme';

export const styles = StyleSheet.create({
  flex: { flex: 1 },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: spacing.screenX - 6,
    paddingTop: 4,
    paddingBottom: 10,
  },
  backBtn: { padding: 4 },
  title: { fontSize: 22, color: colors.ink },
  content: {
    paddingHorizontal: spacing.screenX,
    paddingTop: 8,
    paddingBottom: 40,
    gap: 16,
  },
  formError: { fontSize: 13, color: colors.dangerText, textAlign: 'center' },
  submit: { marginTop: 8 },
});
