package setty.notification.send.fcm;

public record FcmDelivery(boolean delivered, boolean tokenInvalid) {

    public static FcmDelivery success() {
        return new FcmDelivery(true, false);
    }

    public static FcmDelivery failure() {
        return new FcmDelivery(false, false);
    }

    public static FcmDelivery invalidToken() {
        return new FcmDelivery(false, true);
    }
}
