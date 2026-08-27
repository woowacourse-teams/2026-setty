import type { ListingItem } from '../api/listings';

export const mockListings: ListingItem[] = [
    {
        id: 1,
        title: '슬림 콘솔 데스크 1000 (오크)',
        thumbnailUrl: '/images/listings/console-desk.png',
        price: 64000,
        deliveryFee: 26000,
        totalPrice: 90000,
        category: 'DESK',
        conditionGrade: 'S',
        dimensions: {
            widthCm: 100,
            depthCm: 45,
            heightCm: 75
        },
        createdAt: '2026-08-25T04:30:00Z'
    },
    {
        id: 2,
        title: '라탄 1인 라운지 체어',
        thumbnailUrl: '/images/listings/rattan-chair.png',
        price: 88000,
        deliveryFee: 27000,
        totalPrice: 115000,
        category: 'CHAIR',
        conditionGrade: 'A',
        dimensions: {
            widthCm: 70,
            depthCm: 75,
            heightCm: 80
        },
        createdAt: '2026-08-24T04:30:00Z'
    }
];
