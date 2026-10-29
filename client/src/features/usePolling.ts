import { useEffect, useRef } from 'react';

/**
 * [enabled]인 동안 화면이 보이면 [intervalMillis]마다 [onTick]을 부른다.
 * 숨겨진 탭에서는 멈추고, 다시 보이면 곧바로 한 번 부른다.
 */
export function usePolling(onTick: () => void, enabled: boolean, intervalMillis = 5_000) {
    const onTickRef = useRef(onTick);
    onTickRef.current = onTick;

    useEffect(() => {
        if (!enabled) return undefined;
        let timer: ReturnType<typeof setInterval> | null = null;
        const start = () => {
            if (timer === null) timer = setInterval(() => onTickRef.current(), intervalMillis);
        };
        const stop = () => {
            if (timer !== null) clearInterval(timer);
            timer = null;
        };
        const onVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                onTickRef.current();
                start();
            } else {
                stop();
            }
        };
        if (document.visibilityState === 'visible') start();
        document.addEventListener('visibilitychange', onVisibilityChange);
        return () => {
            stop();
            document.removeEventListener('visibilitychange', onVisibilityChange);
        };
    }, [enabled, intervalMillis]);
}
