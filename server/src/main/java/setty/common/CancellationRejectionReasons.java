package setty.common;

/** Existing cancellation rejection wire codes shared by order and delivery events. */
public final class CancellationRejectionReasons {
    private CancellationRejectionReasons() {}

    public static final String DRIVER_ACCEPTED_OR_LATER = "DRIVER_ACCEPTED_OR_LATER";
    public static final String DRIVER_ACCEPTED = "DRIVER_ACCEPTED";
    public static final String ALREADY_PICKED_UP = "ALREADY_PICKED_UP";
    public static final String ALREADY_DELIVERED = "ALREADY_DELIVERED";
    public static final String STATE_NOT_SYNCHRONIZED = "STATE_NOT_SYNCHRONIZED";
}
