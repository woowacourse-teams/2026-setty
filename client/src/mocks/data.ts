// 목 모드에서 쓰는 메모리 데이터. 새로고침하면 처음 상태로 돌아간다.

export type MockMember = {
    id: number;
    loginId: string;
    password: string;
    phoneNumber: string;
    address: string;
    dongCode: string | null;
};

export type MockListing = {
    id: number;
    sellerId: number;
    title: string;
    description: string;
    price: number;
    category: string;
    conditionGrade: string;
    dimensions: { widthCm: number; depthCm: number; heightCm: number };
    saleStatus: 'AVAILABLE' | 'RESERVED' | 'SOLD';
    images: Array<{ id: number; url: string; displayOrder: number }>;
    dongCode: string | null;
    createdAt: string;
    updatedAt: string;
    deleted: boolean;
};

export type MockKeyword = {
    id: number;
    memberId: number;
    keyword: string;
    category: string | null;
    minPrice: number | null;
    maxPrice: number | null;
    maxDeliveryFee: number | null;
    maxTotalPrice: number | null;
    excludeKeywords: string[];
    createdAt: string;
};

export type MockNotification = {
    id: number;
    memberId: number;
    listingId: number;
    status: 'PENDING' | 'SENT' | 'FAILED';
    createdAt: string;
    ackedAt: string | null;
    readAt: string | null;
};

export type MockOrder = {
    id: number;
    listingId: number;
    buyerId: number;
    orderStatus: 'PENDING' | 'CONFIRMED' | 'CANCEL_PENDING' | 'CANCELLED' | 'EXPIRED';
    deliveryStatus: 'PENDING' | 'REQUESTED' | 'ACCEPTED' | 'PICKED_UP' | 'DELIVERED';
    deliveryFee: number;
    totalPrice: number;
};

export const dongs = [
    { code: '1111051500', name: '청운효자동', district: '종로구' },
    { code: '1111053000', name: '사직동', district: '종로구' },
    { code: '1111054000', name: '삼청동', district: '종로구' },
    { code: '1144066000', name: '서교동', district: '마포구' },
    { code: '1144068000', name: '합정동', district: '마포구' },
    { code: '1144069000', name: '망원1동', district: '마포구' },
    { code: '1144070000', name: '망원2동', district: '마포구' },
    { code: '1120069000', name: '성수1가1동', district: '성동구' },
    { code: '1120070000', name: '성수2가1동', district: '성동구' },
    { code: '1168064000', name: '역삼1동', district: '강남구' },
    { code: '1168058000', name: '삼성1동', district: '강남구' },
    { code: '1168051000', name: '신사동', district: '강남구' },
    { code: '1171063100', name: '잠실본동', district: '송파구' },
    { code: '1171071000', name: '잠실2동', district: '송파구' },
    { code: '1150060300', name: '화곡1동', district: '강서구' }
];

export const members: MockMember[] = [
    { id: 1, loginId: 'setty01', password: 'password1', phoneNumber: '010-1234-5678', address: '서울 마포구 와우산로 94', dongCode: '1144066000' },
    { id: 2, loginId: 'seller01', password: 'password1', phoneNumber: '010-2222-3333', address: '서울 성동구 성수이로 113', dongCode: '1120069000' },
    // 행정동을 고르기 전에 가입한 회원. 배송비 필터를 쓰면 정보 수정으로 보낸다
    { id: 3, loginId: 'nodong01', password: 'password1', phoneNumber: '010-4444-5555', address: '서울 강서구 화곡로 302', dongCode: null }
];

const palette = ['#8d6e63', '#6d4c41', '#a1887f', '#5d4037', '#bcaaa4', '#795548', '#9e9d24', '#546e7a', '#7e57c2', '#26a69a'];

/** 실제 사진 대신 쓸 단색 SVG 이미지. */
function placeholderImage(label: string, seed: number): string {
    const color = palette[seed % palette.length];
    const accent = palette[(seed + 3) % palette.length];
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600">`
        + `<rect width="600" height="600" fill="${color}"/>`
        + `<rect x="120" y="260" width="360" height="160" rx="24" fill="${accent}" opacity="0.85"/>`
        + `<rect x="150" y="420" width="24" height="70" rx="8" fill="${accent}"/>`
        + `<rect x="426" y="420" width="24" height="70" rx="8" fill="${accent}"/>`
        + `<text x="300" y="200" font-family="sans-serif" font-size="56" font-weight="700" fill="white" text-anchor="middle">${label}</text>`
        + `</svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

const seeds: Array<[string, string, number, string, [number, number, number], string]> = [
    ['원목 4인용 식탁', 'TABLE', 120_000, 'A', [140, 80, 75], '2년 사용한 원목 식탁입니다.\n모서리 찍힘 없이 깨끗하게 사용했어요.'],
    ['패브릭 3인용 소파', 'SOFA', 250_000, 'S', [210, 90, 85], '이사로 내놓습니다. 커버 세탁 가능해요.'],
    ['화이트 5단 수납장', 'STORAGE', 45_000, 'B', [80, 40, 180], '문 한 짝 경첩이 조금 헐거워요.'],
    ['모션 데스크 1400', 'DESK', 180_000, 'A', [140, 70, 73], '높이 조절 잘 됩니다. 메모리 버튼 4개.'],
    ['메쉬 사무용 의자', 'CHAIR', 60_000, 'B', [65, 65, 120], '팔걸이 높이 조절 가능. 사용감 있어요.'],
    ['퀸 침대 프레임', 'BED', 150_000, 'A', [160, 210, 40], '매트리스는 포함되지 않아요.'],
    ['라탄 협탁', 'TABLE', 25_000, 'S', [40, 40, 50], '거의 새것이에요.'],
    ['2인용 가죽 소파', 'SOFA', 90_000, 'C', [150, 85, 80], '가죽 갈라짐이 조금 있어요.'],
    ['원목 책장 3단', 'STORAGE', 35_000, 'B', [90, 30, 110], '조립식이라 분해해서 옮기기 쉬워요.'],
    ['접이식 캠핑 테이블', 'TABLE', 20_000, 'A', [90, 60, 45], '베란다 테이블로 쓰기 좋아요.'],
    ['게이밍 의자', 'CHAIR', 80_000, 'A', [70, 70, 130], '리클라이닝 됩니다.'],
    ['싱글 침대 + 매트리스', 'BED', 70_000, 'B', [100, 200, 45], '매트리스 커버 씌워서 사용했어요.'],
    ['L자 코너 책상', 'DESK', 110_000, 'B', [160, 120, 75], '모서리 쪽 상판에 작은 흠집 있어요.'],
    ['유리 거실 테이블', 'TABLE', 55_000, 'A', [100, 50, 40], '강화유리 상판입니다.'],
    ['원목 스툴 2개', 'CHAIR', 30_000, 'S', [35, 35, 45], '두 개 일괄 판매합니다.'],
    ['6단 서랍장', 'STORAGE', 65_000, 'A', [80, 45, 120], '서랍 레일 부드러워요.'],
    ['모듈 소파 1인', 'SOFA', 85_000, 'A', [90, 90, 70], '추가 모듈과 연결 가능해요.'],
    ['아이 책상 세트', 'DESK', 50_000, 'B', [100, 60, 75], '의자 포함입니다.'],
    ['원형 식탁', 'TABLE', 75_000, 'A', [100, 100, 75], '2~3인용으로 쓰기 좋아요.'],
    ['수퍼싱글 침대', 'BED', 95_000, 'A', [110, 200, 40], '헤드 수납 가능.'],
    ['벤치형 의자', 'CHAIR', 40_000, 'B', [120, 35, 45], '식탁 의자로 쓰던 벤치예요.'],
    ['TV 거실장', 'STORAGE', 70_000, 'A', [180, 40, 50], '선 정리 홀이 있어요.'],
    ['리클라이너 소파', 'SOFA', 300_000, 'S', [95, 95, 100], '전동 리클라이너입니다.'],
    ['원목 화장대', 'DESK', 60_000, 'B', [90, 40, 140], '거울 포함.'],
    ['폴딩 의자 4개', 'CHAIR', 28_000, 'A', [45, 45, 80], '손님용으로 보관만 했어요.']
];

const now = Date.UTC(2026, 9, 22, 3, 0, 0);
let nextImageId = 1000;

export const listings: MockListing[] = seeds.map(([title, category, price, grade, [w, d, h], description], index) => {
    const createdAt = new Date(now - index * 9 * 3600_000).toISOString();
    const imageCount = index % 3 === 2 ? 1 : 3;
    return {
        id: index + 1,
        sellerId: index % 4 === 0 ? 1 : 2,
        title,
        description,
        price,
        category,
        conditionGrade: grade,
        dimensions: { widthCm: w, depthCm: d, heightCm: h },
        saleStatus: index === 7 ? 'SOLD' : 'AVAILABLE',
        images: Array.from({ length: imageCount }, (_, imageIndex) => ({
            id: nextImageId++,
            url: placeholderImage(title.split(' ').slice(-1)[0], index + imageIndex),
            displayOrder: imageIndex
        })),
        dongCode: index === 5 ? null : dongs[index % dongs.length].code,
        createdAt,
        updatedAt: createdAt,
        deleted: false
    };
});

export function newImageId(): number {
    return nextImageId++;
}

export const favorites = new Map<number, Set<number>>([[1, new Set([2, 4])]]);

export const orders: MockOrder[] = [
    { id: 501, listingId: 6, buyerId: 1, orderStatus: 'CONFIRMED', deliveryStatus: 'PICKED_UP', deliveryFee: 30_000, totalPrice: 180_000 },
    { id: 502, listingId: 10, buyerId: 1, orderStatus: 'CONFIRMED', deliveryStatus: 'REQUESTED', deliveryFee: 10_000, totalPrice: 30_000 },
    { id: 503, listingId: 15, buyerId: 1, orderStatus: 'CANCELLED', deliveryStatus: 'REQUESTED', deliveryFee: 10_000, totalPrice: 40_000 }
];

/** 가구 부피에 따라 정액으로 정하는 배송비. 서버의 배송비 정책을 흉내낸다. */
export function deliveryFeeOf(listing: MockListing): number {
    const { widthCm, depthCm, heightCm } = listing.dimensions;
    const cubicMeters = (widthCm * depthCm * heightCm) / 1_000_000;
    if (cubicMeters <= 0.3) return 10_000;
    if (cubicMeters <= 0.8) return 20_000;
    if (cubicMeters <= 1.5) return 30_000;
    return 50_000;
}

export const keywords: MockKeyword[] = [
    { id: 901, memberId: 1, keyword: '소파', category: 'SOFA', minPrice: 100_000, maxPrice: 300_000, maxDeliveryFee: 20_000, maxTotalPrice: null, excludeKeywords: ['가죽', '중고'], createdAt: new Date(now - 3 * 86_400_000).toISOString() },
    { id: 902, memberId: 1, keyword: '책상', category: null, minPrice: null, maxPrice: null, maxDeliveryFee: null, maxTotalPrice: null, excludeKeywords: [], createdAt: new Date(now - 2 * 86_400_000).toISOString() }
];

export const notifications: MockNotification[] = [
    { id: 7_001, memberId: 1, listingId: 2, status: 'SENT', createdAt: new Date(now - 30 * 60_000).toISOString(), ackedAt: null, readAt: null },
    { id: 7_002, memberId: 1, listingId: 4, status: 'SENT', createdAt: new Date(now - 5 * 3_600_000).toISOString(), ackedAt: null, readAt: null },
    { id: 7_003, memberId: 1, listingId: 8, status: 'SENT', createdAt: new Date(now - 2 * 86_400_000).toISOString(), ackedAt: new Date(now - 86_400_000).toISOString(), readAt: new Date(now - 86_400_000).toISOString() }
];

let nextKeywordId = 1_000;

export function newKeywordId(): number {
    return nextKeywordId++;
}
