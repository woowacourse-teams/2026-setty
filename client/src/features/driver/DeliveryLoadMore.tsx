import { useEffect, useRef } from 'react';
import { Button, LoadingIndicator } from '../../ui/components';
import styles from './driver.module.css';

export function DeliveryLoadMore({ hasMore, isLoading, hasError, onLoad }: {
    hasMore: boolean;
    isLoading: boolean;
    hasError: boolean;
    onLoad: () => void;
}) {
    const trigger = useRef<HTMLDivElement>(null);
    useEffect(() => {
        if (!hasMore || isLoading || hasError || !trigger.current) return;
        const observer = new IntersectionObserver(([entry]) => {
            if (entry.isIntersecting) onLoad();
        }, { rootMargin: '200px' });
        observer.observe(trigger.current);
        return () => observer.disconnect();
    }, [hasMore, isLoading, hasError, onLoad]);

    if (!hasMore) return null;
    return (
        <div className={styles['load-more']} ref={trigger}>
            {isLoading ? <LoadingIndicator /> : <Button onClick={onLoad} variant="text">{hasError ? '다시 불러오기' : '더 불러오기'}</Button>}
        </div>
    );
}
