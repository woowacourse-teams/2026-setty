import { forwardRef } from 'react';
import { StyleSheet, TextInput, TextInputProps, View } from 'react-native';
import { colors, fonts, radius } from '@/theme';
import { AppText } from './AppText';

interface TextFieldProps extends TextInputProps {
  label: string;
  /** 있으면 에러 문구를 빨갛게 표시하고 테두리를 강조한다. */
  error?: string;
  /** error가 없을 때만 회색 힌트로 표시한다. */
  hint?: string;
}

/** 라벨 + 입력 + (에러|힌트) 한 벌. 로그인·회원가입 폼에서 쓴다. */
export const TextField = forwardRef<TextInput, TextFieldProps>(function TextField(
  { label, error, hint, style, ...rest },
  ref,
) {
  return (
    <View style={styles.field}>
      <AppText variant="bold" style={styles.label}>
        {label}
      </AppText>
      <TextInput
        ref={ref}
        placeholderTextColor={colors.textFaint}
        style={[styles.input, !!error && styles.inputError, style]}
        {...rest}
      />
      {error ? (
        <AppText variant="medium" style={styles.error}>
          {error}
        </AppText>
      ) : hint ? (
        <AppText variant="medium" style={styles.hint}>
          {hint}
        </AppText>
      ) : null}
    </View>
  );
});

const styles = StyleSheet.create({
  field: { gap: 7 },
  label: { fontSize: 13, color: colors.teal },
  input: {
    height: 54,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    borderColor: colors.inputBorder,
    backgroundColor: colors.white,
    paddingHorizontal: 16,
    fontSize: 16,
    fontFamily: fonts.regular,
    color: colors.ink,
  },
  inputError: { borderColor: colors.danger },
  error: { fontSize: 12, color: colors.dangerText },
  hint: { fontSize: 12, color: colors.textFaint },
});
