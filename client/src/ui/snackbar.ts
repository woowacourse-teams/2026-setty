import { useSyncExternalStore } from 'react';

export type SnackbarMessage = {
    id: number;
    message: string;
    actionLabel?: string;
    onAction?: () => void;
    duration: number;
    // 이동하기 직전에 띄운 안내라 다음 화면까지 남겨야 하는지
    keepOnNextScreen: boolean;
};

type ShowOptions = {
    actionLabel?: string;
    onAction?: () => void;
    long?: boolean;
    keepOnNextScreen?: boolean;
};

const SHORT_DURATION_MILLIS = 4_000;
const LONG_DURATION_MILLIS = 10_000;

let current: SnackbarMessage | null = null;
let nextId = 1;
let dismissTimer: ReturnType<typeof setTimeout> | null = null;
const listeners = new Set<() => void>();

function emit() {
    listeners.forEach((listener) => listener());
}

/** 화면 아래에 잠깐 보여주는 안내. 새 안내가 오면 이전 안내를 바로 바꾼다. */
export function showSnackbar(message: string, options: ShowOptions = {}) {
    if (dismissTimer) clearTimeout(dismissTimer);
    const duration = options.long ? LONG_DURATION_MILLIS : SHORT_DURATION_MILLIS;
    current = {
        id: nextId++,
        message,
        actionLabel: options.actionLabel,
        onAction: options.onAction,
        duration,
        keepOnNextScreen: options.keepOnNextScreen ?? false
    };
    dismissTimer = setTimeout(dismissSnackbar, duration);
    emit();
}

/** 화면이 바뀌면 이전 화면의 안내를 닫는다. 다음 화면에 보여주려고 띄운 안내는 한 번만 남긴다. */
export function onScreenChanged() {
    if (!current) return;
    if (current.keepOnNextScreen) {
        current = { ...current, keepOnNextScreen: false };
        return;
    }
    dismissSnackbar();
}

export function dismissSnackbar() {
    if (dismissTimer) clearTimeout(dismissTimer);
    dismissTimer = null;
    current = null;
    emit();
}

export function useSnackbar(): SnackbarMessage | null {
    return useSyncExternalStore(
        (listener) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
        () => current
    );
}
