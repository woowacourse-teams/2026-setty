type ListingStatus = '판매중' | '예약중';

type Product = {
    id: number;
    title: string;
    price: number;
    deliveryFee: number;
    category: string;
    condition: string;
    registeredAt: string;
    status: ListingStatus;
    imageCount: number;
    image: string;
};

const products: Product[] = [
    { id: 1, title: '슬림 콘솔 데스크 1000 (오크)', price: 64000, deliveryFee: 26000, category: '책상', condition: 'S급', registeredAt: '8.25', status: '판매중', imageCount: 2, image: '/images/listings/console-desk.png' },
    { id: 2, title: '원너 사이드 테이블 2개 세트', price: 58000, deliveryFee: 21000, category: '테이블', condition: 'S급', registeredAt: '8.25', status: '판매중', imageCount: 2, image: '/images/listings/side-table.png' },
    { id: 3, title: '라탄 1인 라운지 체어', price: 88000, deliveryFee: 27000, category: '의자', condition: 'A급', registeredAt: '8.24', status: '판매중', imageCount: 3, image: '/images/listings/rattan-chair.png' },
    { id: 4, title: '북유럽 스타일 3인 패브릭 소파 (라이트그레이)', price: 240000, deliveryFee: 63000, category: '소파', condition: 'A급', registeredAt: '8.24', status: '판매중', imageCount: 4, image: '/images/listings/gray-sofa.png' },
    { id: 5, title: '메쉬 오피스 의자 (헤드레스트 포함)', price: 95000, deliveryFee: 24000, category: '의자', condition: 'A급', registeredAt: '8.22', status: '판매중', imageCount: 2, image: '/images/listings/office-chair.png' },
    { id: 6, title: '침실용 2단 협탁 (무광 화이트)', price: 42000, deliveryFee: 19000, category: '수납', condition: 'A급', registeredAt: '8.22', status: '판매중', imageCount: 2, image: '/images/listings/nightstand.png' },
    { id: 7, title: '3단 원목 서랍장 화이트오크', price: 130000, deliveryFee: 39000, category: '수납', condition: 'B급', registeredAt: '8.20', status: '예약중', imageCount: 3, image: '/images/listings/white-drawer.png' },
    { id: 8, title: '2인 리클라이너 가죽 소파 (다크브라운)', price: 310000, deliveryFee: 66000, category: '소파', condition: 'B급', registeredAt: '8.19', status: '판매중', imageCount: 3, image: '/images/listings/recliner.png' },
    { id: 9, title: '월넛 타원 다이닝 테이블 1400', price: 176000, deliveryFee: 47000, category: '테이블', condition: 'A급', registeredAt: '8.18', status: '판매중', imageCount: 3, image: '/images/listings/dining-table.png' },
    { id: 10, title: '라운드 원목 TV 거실장', price: 148000, deliveryFee: 42000, category: '수납', condition: 'A급', registeredAt: '8.17', status: '판매중', imageCount: 4, image: '/images/listings/tv-console.png' },
    { id: 11, title: '크림 부클레 라운지 암체어', price: 119000, deliveryFee: 35000, category: '의자', condition: 'A급', registeredAt: '8.16', status: '판매중', imageCount: 2, image: '/images/listings/boucle-chair.png' },
    { id: 12, title: '브라스 아치 플로어 램프', price: 68000, deliveryFee: 18000, category: '조명', condition: 'S급', registeredAt: '8.15', status: '판매중', imageCount: 2, image: '/images/listings/floor-lamp.png' },
];

const productColumns = [
    [products[0], products[1], products[8]],
    [products[2], products[3], products[9]],
    [products[4], products[5], products[10]],
    [products[6], products[7], products[11]],
];

function formatWon(value: number) {
    return `${value.toLocaleString('ko-KR')}원`;
}

type ProductCardProps = {
    product: Product;
    onSelect: () => void;
};

function ProductCard({ product, onSelect }: ProductCardProps) {
    const totalPrice = product.price + product.deliveryFee;

    return (
        <article
            className="product-card product-card--interactive"
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
            <div className="product-card__image-wrap">
                <img className="product-card__image" src={product.image} alt={product.title} />
                <span className={`status-badge ${product.status === '예약중' ? 'status-badge--reserved' : ''}`}>
                    {product.status}
                </span>
                <span className="image-count">이미지 {product.imageCount}장</span>
            </div>
            <div className="product-card__details">
                <h2 className="product-card__title">{product.title}</h2>
                <p className="product-card__price">
                    {formatWon(product.price)} <span>+ 배송 {formatWon(product.deliveryFee)}</span>
                </p>
                <p className="product-card__total">총 {formatWon(totalPrice)}</p>
                <p className="product-card__meta">{product.category} <i /> {product.condition} <i /> {product.registeredAt} 등록</p>
            </div>
        </article>
    );
}

type ProductGridProps = {
    onProductSelect: () => void;
};

export function ProductGrid({ onProductSelect }: ProductGridProps) {
    return (
        <section className="product-grid" aria-label="판매 중인 가구 목록">
            {productColumns.map((column, index) => (
                <div className="product-grid__column" key={index}>
                    {column.map((product) => <ProductCard key={product.id} product={product} onSelect={onProductSelect} />)}
                </div>
            ))}
        </section>
    );
}
