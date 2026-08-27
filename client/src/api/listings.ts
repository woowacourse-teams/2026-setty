export type ListingItem = {
    id: number;
    title: string;
    thumbnailUrl: string | null;
    price: number;
    deliveryFee: number;
    totalPrice: number;
    category: string;
    conditionGrade: string;
    dimensions: {
        widthCm: number;
        depthCm: number;
        heightCm: number;
    };
    createdAt: string;
};

type ListingListResponse = {
    items: ListingItem[];
};

export async function fetchListings(): Promise<ListingItem[]> {
    const response = await fetch('/api/listings');

    if (!response.ok) {
        throw new Error('매물 목록을 불러오지 못했습니다.');
    }

    const body: ListingListResponse = await response.json();
    return body.items;
}
