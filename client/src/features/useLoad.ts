import { useCallback, useEffect, useRef, useState } from 'react';
import { isUnauthorized, toLoadError, toUserMessage, type LoadState } from '../api/errors';
import { useNavigateToLogin } from '../navigation';
import { showSnackbar } from '../ui/snackbar';

/**
 * 화면에 들어올 때 불러오고, 당겨서 새로고침하는 데이터.
 * 이미 보여주는 데이터가 있을 때 새로고침이 실패하면 데이터를 유지한 채 알리고, 로그인이 풀렸으면 로그인으로 보낸다.
 */
export function useLoad<T>(fetcher: () => Promise<T>) {
    const [state, setState] = useState<LoadState<T>>({ status: 'loading' });
    const [isRefreshing, setIsRefreshing] = useState(false);
    const navigateToLogin = useNavigateToLogin();
    const stateRef = useRef(state);
    stateRef.current = state;
    const fetcherRef = useRef(fetcher);
    fetcherRef.current = fetcher;

    const fetch = useCallback(async (): Promise<T | null> => {
        try {
            const data = await fetcherRef.current();
            setState({ status: 'success', data });
            setIsRefreshing(false);
            return data;
        } catch (error) {
            const hasData = stateRef.current.status === 'success';
            if (!hasData) setState(toLoadError(error));
            setIsRefreshing(false);
            if (hasData) {
                if (isUnauthorized(error)) {
                    navigateToLogin();
                } else {
                    showSnackbar(toUserMessage(error));
                }
            }
            return null;
        }
    }, [navigateToLogin]);

    const load = useCallback(() => {
        setState({ status: 'loading' });
        void fetch();
    }, [fetch]);

    const refresh = useCallback(() => {
        setIsRefreshing(true);
        void fetch();
    }, [fetch]);

    useEffect(load, [load]);

    /** 낙관적으로 바꾼 데이터를 바로 보여준다. */
    const setData = useCallback((transform: (data: T) => T) => {
        setState((current) => current.status === 'success' ? { status: 'success', data: transform(current.data) } : current);
    }, []);

    return { state, isRefreshing, load, refresh, fetch, setData };
}
