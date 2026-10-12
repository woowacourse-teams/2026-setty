package setty.deliveryfee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "setty.delivery-fee")
public record DeliveryFeeProperties(
        int baseFee,
        int stepPrice,
        int volumeTierSteps,
        int freeDistanceMeters,
        int stepDistanceMeters
) {

    public DeliveryFeeProperties {
        if (baseFee < 0 || stepPrice <= 0 || volumeTierSteps < 0 || freeDistanceMeters < 0 || stepDistanceMeters <= 0) {
            throw new IllegalArgumentException("배송비 설정이 올바르지 않습니다");
        }
    }
}
