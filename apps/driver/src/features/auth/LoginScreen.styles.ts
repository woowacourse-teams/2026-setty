import { StyleSheet } from 'react-native';
import { colors, spacing } from '@/theme';

export const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: spacing.screenX,
    paddingVertical: 40,
    gap: 36,
  },
  hero: { alignItems: 'center', gap: 8 },
  brand: { fontSize: 34, color: colors.ink },
  subtitle: { fontSize: 14, color: colors.textMuted },
  form: { gap: 18 },
  error: { fontSize: 13, color: colors.dangerText, textAlign: 'center' },
  submit: { marginTop: 6 },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
  },
  footerText: { fontSize: 14, color: colors.textMuted },
  footerLink: { fontSize: 14, color: colors.brand },
});
