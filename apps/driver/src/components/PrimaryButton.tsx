import { ActivityIndicator, Pressable, StyleSheet, ViewStyle } from 'react-native';
import { colors, radius, shadows } from '@/theme';
import { AppText } from './AppText';

interface PrimaryButtonProps {
  label: string;
  onPress: () => void;
  /** ghost: 외곽선 스타일(거절 등 보조 액션). */
  variant?: 'solid' | 'ghost';
  loading?: boolean;
  disabled?: boolean;
  style?: ViewStyle;
}

/** 주요 액션 버튼(수락) + 보조(거절) 겸용. */
export function PrimaryButton({
  label,
  onPress,
  variant = 'solid',
  loading = false,
  disabled = false,
  style,
}: PrimaryButtonProps) {
  const isGhost = variant === 'ghost';
  const inactive = disabled || loading;

  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      style={({ pressed }) => [
        styles.base,
        isGhost ? styles.ghost : styles.solid,
        !isGhost && !inactive && shadows.floating,
        pressed && !inactive && (isGhost ? styles.ghostPressed : styles.solidPressed),
        inactive && styles.inactive,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={isGhost ? colors.textMuted : colors.white} />
      ) : (
        <AppText
          variant="display"
          style={[styles.label, { color: isGhost ? colors.textMuted : colors.white }]}
        >
          {label}
        </AppText>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    height: 58,
    borderRadius: radius.xl,
    alignItems: 'center',
    justifyContent: 'center',
  },
  solid: { backgroundColor: colors.brand },
  solidPressed: { backgroundColor: colors.brandPressed },
  ghost: {
    backgroundColor: colors.white,
    borderWidth: 2,
    borderColor: colors.inputBorder,
  },
  ghostPressed: { backgroundColor: '#F1F7F6' },
  inactive: { opacity: 0.55 },
  label: { fontSize: 19 },
});
