package setty.platform.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeliveryFeePolicyTest {

    @DisplayName("부피가 300,000 세제곱센티미터이면 배송비는 10,000원이다")
    @Test
    void calculatesSmallDeliveryFeeAtUpperBoundary() {
        Dimensions dimensions = Dimensions.of(300, 1_000, 1);

        int deliveryFee = DeliveryFeePolicy.calculate(dimensions);

        assertThat(deliveryFee).isEqualTo(10_000);
    }

    @DisplayName("부피가 300,000 세제곱센티미터를 초과하면 배송비는 20,000원이다")
    @Test
    void calculatesMediumDeliveryFeeAboveSmallBoundary() {
        Dimensions dimensions = Dimensions.of(301, 1_000, 1);

        int deliveryFee = DeliveryFeePolicy.calculate(dimensions);

        assertThat(deliveryFee).isEqualTo(20_000);
    }

    @DisplayName("부피가 1,000,000 세제곱센티미터이면 배송비는 20,000원이다")
    @Test
    void calculatesMediumDeliveryFeeAtUpperBoundary() {
        Dimensions dimensions = Dimensions.of(1_000, 1_000, 1);

        int deliveryFee = DeliveryFeePolicy.calculate(dimensions);

        assertThat(deliveryFee).isEqualTo(20_000);
    }

    @DisplayName("부피가 1,000,000 세제곱센티미터를 초과하면 배송비는 30,000원이다")
    @Test
    void calculatesLargeDeliveryFeeAboveMediumBoundary() {
        Dimensions dimensions = Dimensions.of(1_000, 1_000, 2);

        int deliveryFee = DeliveryFeePolicy.calculate(dimensions);

        assertThat(deliveryFee).isEqualTo(30_000);
    }
}
