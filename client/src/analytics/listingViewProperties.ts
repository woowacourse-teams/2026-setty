import type { FurnitureDetail } from '../model/furniture';

const HIGH_TRANSPORT_BURDEN_LONGEST_SIDE_CM = 150;

function priceBucketOf(price: number) {
    if (price < 100_000) return 'under_100k';
    if (price < 300_000) return '100k_to_300k';
    return '300k_plus';
}

export function listingViewProperties(listing: FurnitureDetail) {
    const longestSideCm = Math.max(listing.widthCm, listing.depthCm, listing.heightCm);
    return {
        listing_id: listing.id,
        transport_burden: longestSideCm >= HIGH_TRANSPORT_BURDEN_LONGEST_SIDE_CM ? 'high' : 'low',
        longest_side_cm: longestSideCm,
        category: listing.category,
        price: listing.price,
        price_bucket: priceBucketOf(listing.price),
        delivery_fee: listing.deliveryFee
    };
}
