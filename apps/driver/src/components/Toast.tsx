import { useEffect, useRef, useState, useCallback } from 'react';
import { Animated, StyleSheet } from 'react-native';
import { colors, radius, shadows } from '@/theme';
import { AppText } from './AppText';

/** 화면 하단 토스트를 다루는 훅. show(message)로 잠깐 띄운다. */
export function useToast(durationMs = 2400) {
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>();

  const show = useCallback(
    (msg: string) => {
      setMessage(msg);
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => setMessage(null), durationMs);
    },
    [durationMs],
  );

  useEffect(() => () => timer.current && clearTimeout(timer.current), []);

  return { message, show };
}

/** 토스트 뷰. message가 null이면 아무것도 렌더하지 않는다. */
export function Toast({ message }: { message: string | null }) {
  const opacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(opacity, {
      toValue: message ? 1 : 0,
      duration: 220,
      useNativeDriver: true,
    }).start();
  }, [message, opacity]);

  if (!message) return null;

  return (
    <Animated.View style={[styles.toast, shadows.floating, { opacity }]}>
      <AppText variant="medium" style={styles.text}>
        {message}
      </AppText>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  toast: {
    position: 'absolute',
    bottom: 40,
    alignSelf: 'center',
    backgroundColor: colors.ink,
    paddingVertical: 13,
    paddingHorizontal: 22,
    borderRadius: radius.lg,
  },
  text: { color: colors.white, fontSize: 13.5 },
});
