import { useEffect, useState } from 'react';
import { fetchListings, type ListingItem } from '../api/listings';
import productGridStyles from '../styles/modules/ProductGrid.module.css';

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

type ProductCardProps = {
    product: ListingItem;
    onSelect: () => void;
};

function ProductCard({ product, onSelect }: ProductCardProps) {
function formatRegisteredAt(createdAt: string) {
    const date = new Date(createdAt);
    return `${date.getUTCMonth() + 1}.${date.getUTCDate()}`;
}

    return (
        <article
            className={[productGridStyles['product-card'], productGridStyles['product-card--interactive']].filter(Boolean).join(' ')}
            role="button"
            tabIndex={0}
            onClick={onSelect}
            onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    onSelect();
                }
            }}
        >
            <div className={productGridStyles['product-card__image-wrap']}>
                {product.thumbnailUrl && (
                    <img className={productGridStyles['product-card__image']} src={product.thumbnailUrl} alt={product.title} />
                )}
            </div>
            <div className={productGridStyles['product-card__details']}>
                <h2 className={productGridStyles['product-card__title']}>{product.title}</h2>
                <p className={productGridStyles['product-card__price']}>
                    {formatWon(product.price)} <span>+ 배송 {formatWon(product.deliveryFee)}</span>
                </p>
                <p className={productGridStyles['product-card__total']}>총 {formatWon(product.totalPrice)}</p>
                <p className={productGridStyles['product-card__meta']}>
                    {categoryLabels[product.category] ?? product.category} <i /> {product.conditionGrade}급 <i /> {formatRegisteredAt(product.createdAt)} 등록
                </p>
            </div>
        </article>
    );
}

function ProductCardSkeleton() {
    return (
        <article className={productGridStyles['product-card']} aria-label="매물 목록을 불러오는 중">
            <div className={[productGridStyles['product-card__image-wrap'], productGridStyles['skeleton']].filter(Boolean).join(' ')} />
            <div className={productGridStyles['product-card__details']}>
                <div className={[productGridStyles['skeleton'], productGridStyles['skeleton--title']].filter(Boolean).join(' ')} />
                <div className={[productGridStyles['skeleton'], productGridStyles['skeleton--price']].filter(Boolean).join(' ')} />
                <div className={[productGridStyles['skeleton'], productGridStyles['skeleton--meta']].filter(Boolean).join(' ')} />
            </div>
        </article>
    );
}

type ProductGridProps = {
    onProductSelect: (listingId: number) => void;
    searchQuery: string;
};

export function ProductGrid({ onProductSelect, searchQuery }: ProductGridProps) {
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
            <section className={productGridStyles['product-grid']} aria-label="판매 중인 가구 목록">
                {Array.from({ length: 12 }, (_, index) => <ProductCardSkeleton key={index} />)}
            </section>
        );
    }

    if (error) {
        return (
            <section className={productGridStyles['product-grid-message']} aria-live="polite">
                <p>{error}</p>
                <button type="button" onClick={() => void load()}>다시 시도</button>
            </section>
        );
    }

    const trimmedSearchQuery = searchQuery.trim();
    const filteredItems = trimmedSearchQuery
        ? items.filter((item) => item.title.toLowerCase().includes(trimmedSearchQuery.toLowerCase()))
        : items;

    if (filteredItems.length === 0) {
        if (trimmedSearchQuery) {
            return <p className={productGridStyles['product-grid-message']}>&ldquo;{trimmedSearchQuery}&rdquo;에 해당하는 매물이 없습니다.</p>;
        }

        return <p className={productGridStyles['product-grid-message']}>판매 중인 매물이 없습니다.</p>;
    }

    return (
        <section className={productGridStyles['product-grid']} aria-label="판매 중인 가구 목록">
            {filteredItems.map((product) => <ProductCard key={product.id} product={product} onSelect={() => onProductSelect(product.id)} />)}
        </section>
    );
}
