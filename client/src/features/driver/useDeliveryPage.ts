import { useLayoutEffect, useSyncExternalStore } from 'react';
import { useNavigateToDriverLogin } from '../../navigation';
import type { createDeliveryPageStore } from './deliveryPageStore';

export function useDeliveryPage(store: ReturnType<typeof createDeliveryPageStore>) {
    const navigateToLogin = useNavigateToDriverLogin();
    const page = useSyncExternalStore(store.subscribe, store.getSnapshot);
    useLayoutEffect(() => { store.enter(navigateToLogin); }, [store, navigateToLogin]);
    return { ...page, load: store.refresh, refresh: store.refresh, loadMore: store.loadMore };
}
