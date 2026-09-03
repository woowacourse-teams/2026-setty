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
        <article className={productGridStyles['product-card']}>
            <a
                className={productGridStyles['product-card__link']}
                href={`/listings/${product.id}`}
                onClick={(event) => {
                    event.preventDefault();
                    onSelect();
                }}
            >
                <div className={productGridStyles['product-card__image-wrap']}>
                    {product.thumbnailUrl && (
                        <img className={productGridStyles['product-card__image']} src={product.thumbnailUrl} alt="" />
                    )}
                </div>
                <div className={productGridStyles['product-card__details']}>
                    <h2 className={productGridStyles['product-card__title']}>{product.title}</h2>
                    <p className={productGridStyles['product-card__price']}>
                        {formatWon(product.price)} <span>+ 배송 {formatWon(product.deliveryFee)}</span>
                    </p>
                    <p className={productGridStyles['product-card__total']}>총 {formatWon(product.totalPrice)}</p>
                    <p className={productGridStyles['product-card__meta']}>
                        {categoryLabels[product.category] ?? product.category} <i aria-hidden="true" /> {product.conditionGrade}급 <i aria-hidden="true" /> {formatRegisteredAt(product.createdAt)} 등록
                    </p>
                </div>
            </a>
        </article>
    );
}

function ProductCardSkeleton() {
    return (
        <article aria-hidden="true" className={productGridStyles['product-card']}>
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
            <>
                <p className="visually-hidden" role="status">판매 중인 가구 목록을 불러오는 중입니다.</p>
                <section aria-busy="true" aria-labelledby="product-grid-title" className={productGridStyles['product-grid']}>
                    <h1 className="visually-hidden" id="product-grid-title">판매 중인 가구</h1>
                    {Array.from({ length: 12 }, (_, index) => <ProductCardSkeleton key={index} />)}
                </section>
            </>
        );
    }

    if (error) {
        return (
            <section className={productGridStyles['product-grid-message']} role="alert">
                <h1 className="visually-hidden">판매 중인 가구</h1>
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
            return (
                <section className={productGridStyles['product-grid-message']}>
                    <h1 className="visually-hidden">판매 중인 가구</h1>
                    <p role="status">&ldquo;{trimmedSearchQuery}&rdquo;에 해당하는 매물이 없습니다.</p>
                </section>
            );
        }

        return (
            <section className={productGridStyles['product-grid-message']}>
                <h1 className="visually-hidden">판매 중인 가구</h1>
                <p role="status">판매 중인 매물이 없습니다.</p>
            </section>
        );
    }

    return (
        <section aria-labelledby="product-grid-title" className={productGridStyles['product-grid']}>
            <h1 className="visually-hidden" id="product-grid-title">판매 중인 가구</h1>
            <p className="visually-hidden" role="status">
                {trimmedSearchQuery
                    ? `${trimmedSearchQuery} 검색 결과 ${filteredItems.length}개`
                    : `판매 중인 가구 ${filteredItems.length}개`}
            </p>
            {filteredItems.map((product) => <ProductCard key={product.id} product={product} onSelect={() => onProductSelect(product.id)} />)}
        </section>
    );
}
