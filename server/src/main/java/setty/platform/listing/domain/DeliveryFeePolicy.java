package setty.platform.listing.domain;

public final class DeliveryFeePolicy {

    private static final long SMALL_VOLUME_LIMIT = 300_000L;
    private static final long MEDIUM_VOLUME_LIMIT = 1_000_000L;
    private static final int SMALL_DELIVERY_FEE = 10_000;
    private static final int MEDIUM_DELIVERY_FEE = 20_000;
    private static final int LARGE_DELIVERY_FEE = 30_000;

    private DeliveryFeePolicy() {
    }

    public static int calculate(final Dimensions dimensions) {
        final long volume = dimensions.volumeCubicCentimeters();
        if (volume <= SMALL_VOLUME_LIMIT) {
            return SMALL_DELIVERY_FEE;
        }
        if (volume <= MEDIUM_VOLUME_LIMIT) {
            return MEDIUM_DELIVERY_FEE;
        }
        return LARGE_DELIVERY_FEE;
    }
}
