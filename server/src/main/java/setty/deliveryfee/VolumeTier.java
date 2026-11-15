package setty.deliveryfee;

// 경계는 listings.volume_tier 생성 컬럼(V1)과, 고정 요금은 delivery_fee CHECK(V1)와 같아야 한다.
// 생성 컬럼과 맞는지는 ListingVolumeTierColumnTest가 본다.
public enum VolumeTier {
    SMALL(10_000),
    MEDIUM(20_000),
    LARGE(30_000);

    private static final long SMALL_VOLUME_LIMIT = 300_000L;
    private static final long MEDIUM_VOLUME_LIMIT = 1_000_000L;

    private final int fixedFee;

    VolumeTier(final int fixedFee) {
        this.fixedFee = fixedFee;
    }

    public static VolumeTier of(final long volumeCubicCentimeters) {
        if (volumeCubicCentimeters <= SMALL_VOLUME_LIMIT) {
            return SMALL;
        }
        if (volumeCubicCentimeters <= MEDIUM_VOLUME_LIMIT) {
            return MEDIUM;
        }
        return LARGE;
    }

    // 거리를 잴 수 없을 때 쓰는 부피 배송비. 매물 등록 때 delivery_fee에 저장한다.
    public int fixedFee() {
        return fixedFee;
    }
}
