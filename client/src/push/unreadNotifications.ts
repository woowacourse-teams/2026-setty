import { useSyncExternalStore } from 'react';
import { tokenStore } from '../api/http';
import { notificationRepository } from '../api/repositories';

let hasUnread = false;
const listeners = new Set<() => void>();

function update(next: boolean) {
    if (hasUnread === next) return;
    hasUnread = next;
    listeners.forEach((listener) => listener());
}

/** 서버에서 안 읽은 알림이 있는지 다시 읽는다. 로그인 전이거나 실패하면 표시를 끈다. */
export async function refreshUnreadNotifications(): Promise<void> {
    if (!tokenStore.get()) {
        update(false);
        return;
    }
    try {
        update((await notificationRepository.countUnread()) > 0);
    } catch {
        update(false);
    }
}

/** 알림 하나를 읽음으로 바꾸고 남은 안 읽은 알림이 있는지 다시 본다. */
export async function readNotification(notificationId: number): Promise<void> {
    await notificationRepository.read(notificationId).catch(() => undefined);
    await refreshUnreadNotifications();
}

export async function readAllNotifications(): Promise<void> {
    update(false);
    await notificationRepository.readAll().catch(() => undefined);
}

export function useHasUnreadNotifications(): boolean {
    return useSyncExternalStore(
        (listener) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
        () => hasUnread
    );
}
