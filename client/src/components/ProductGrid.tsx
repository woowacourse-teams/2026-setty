import { useEffect, useState } from 'react';
import { fetchListings, type ListingItem } from '../api/listings';

const categoryLabels: Record<string, string> = {
    SOFA: '소파',
    TABLE: '테이블',
    DESK: '책상',
    CHAIR: '의자',
    STORAGE: '수납장',
    BED: '침대'
};

function formatWon(value: number) {
    return `${value.toLocaleString('ko-KR')}원`;
}

function formatRegisteredAt(createdAt: string) {
    const date = new Date(createdAt);
    return `${date.getUTCMonth() + 1}.${date.getUTCDate()}`;
}

function ProductCard({ product }: { product: ListingItem }) {

    return (
        <article className="product-card">
            <div className="product-card__image-wrap">
                {product.thumbnailUrl && (
                    <img className="product-card__image" src={product.thumbnailUrl} alt={product.title} />
                )}
            </div>
            <div className="product-card__details">
                <h2 className="product-card__title">{product.title}</h2>
                <p className="product-card__price">
                    {formatWon(product.price)} <span>+ 배송 {formatWon(product.deliveryFee)}</span>
                </p>
                <p className="product-card__total">총 {formatWon(product.totalPrice)}</p>
                <p className="product-card__meta">
                    {categoryLabels[product.category] ?? product.category} <i /> {product.conditionGrade}급 <i /> {formatRegisteredAt(product.createdAt)} 등록
                </p>
            </div>
        </article>
    );
}

function ProductCardSkeleton() {
    return (
        <article className="product-card" aria-label="매물 목록을 불러오는 중">
            <div className="product-card__image-wrap skeleton" />
            <div className="product-card__details">
                <div className="skeleton skeleton--title" />
                <div className="skeleton skeleton--price" />
                <div className="skeleton skeleton--meta" />
            </div>
        </article>
    );
}

export function ProductGrid() {
    const [items, setItems] = useState<ListingItem[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    async function load() {
        setIsLoading(true);
        setError(null);

        try {
            setItems(await fetchListings());
        } catch (error) {
            setError(error instanceof Error ? error.message : '매물 목록을 불러오지 못했습니다.');
        } finally {
            setIsLoading(false);
        }
    }

    useEffect(() => {
        void load();
    }, []);

    if (isLoading) {
        return (
            <section className="product-grid" aria-label="판매 중인 가구 목록">
                {Array.from({ length: 12 }, (_, index) => <ProductCardSkeleton key={index} />)}
            </section>
        );
    }

    if (error) {
        return (
            <section className="product-grid-message" aria-live="polite">
                <p>{error}</p>
                <button type="button" onClick={() => void load()}>다시 시도</button>
            </section>
        );
    }

    if (items.length === 0) {
        return <p className="product-grid-message">판매 중인 매물이 없습니다.</p>;
    }

    return (
        <section className="product-grid" aria-label="판매 중인 가구 목록">
            {items.map((product) => <ProductCard key={product.id} product={product} />)}
        </section>
    );
}
