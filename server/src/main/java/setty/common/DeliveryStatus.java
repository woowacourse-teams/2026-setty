package setty.common;

public enum DeliveryStatus {

    PENDING("결제 대기"),
    REQUESTED("배송 요청"),
    ACCEPTED("배차"),
    PICKED_UP("수령"),
    DELIVERED("배송 완료");

    private final String label;

    DeliveryStatus(final String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
