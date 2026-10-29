import { useEffect, useRef, useState } from 'react';
import { furnitureRepository } from '../../api/repositories';
import type { ConditionGrade, FurnitureCategory, FurniturePage } from '../../model/furniture';
import type { PriceRange } from './priceRange';

type SearchState = FurniturePage & { key: string; loading: boolean; error: boolean };

export function useListingSearch(
    query: string, category: FurnitureCategory | null, conditionGrades: readonly ConditionGrade[],
    priceRange: PriceRange | null, maxDeliveryFee: number | null
) {
    const keyword = query.trim();
    const minPrice = priceRange?.min ?? null;
    const maxPrice = priceRange?.max ?? null;
    const key = JSON.stringify([keyword, category, conditionGrades, minPrice, maxPrice, maxDeliveryFee]);
    const [state, setState] = useState<SearchState>({ key: '', items: [], nextCursor: null, loading: false, error: false });
    const [retry, setRetry] = useState(0);
    const generation = useRef(0);
    const activeKey = useRef(key);
    activeKey.current = key;
    const loadingMore = useRef(false);

    useEffect(() => {
        const request = ++generation.current;
        loadingMore.current = false;
        setState({ key, items: [], nextCursor: null, loading: !!keyword, error: false });
        if (!keyword) return;
        const timer = window.setTimeout(() => {
            furnitureRepository.getFurniturePage({ query: keyword, category, conditionGrades, minPrice, maxPrice, maxDeliveryFee })
                .then((page) => {
                    if (request !== generation.current || activeKey.current !== key) return;
                    setState({ ...page, key, loading: false, error: false });
                })
                .catch(() => {
                    if (request !== generation.current || activeKey.current !== key) return;
                    setState({ key, items: [], nextCursor: null, loading: false, error: true });
                });
        }, 300);
        return () => { window.clearTimeout(timer); generation.current += 1; };
    }, [key, retry]);

    const loadMore = () => {
        if (activeKey.current !== key || state.key !== key || state.loading || loadingMore.current || state.nextCursor === null) return;
        loadingMore.current = true;
        const request = generation.current;
        setState((current) => ({ ...current, loading: true, error: false }));
        furnitureRepository.getFurniturePage({ query: keyword, category, conditionGrades, minPrice, maxPrice, maxDeliveryFee, cursor: state.nextCursor })
            .then((page) => {
                if (request !== generation.current || activeKey.current !== key) return;
                loadingMore.current = false;
                setState((current) => {
                    const seen = new Set(current.items.map((item) => item.id));
                    return { key, items: [...current.items, ...page.items.filter((item) => !seen.has(item.id))],
                        nextCursor: page.nextCursor, loading: false, error: false };
                });
            })
            .catch(() => {
                if (request !== generation.current || activeKey.current !== key) return;
                loadingMore.current = false;
                setState((current) => ({ ...current, loading: false, error: true }));
            });
    };

    return { ...(state.key === key ? state : { items: [], nextCursor: null, loading: !!keyword, error: false }),
        loadMore, retry: () => setRetry((current) => current + 1) };
}
