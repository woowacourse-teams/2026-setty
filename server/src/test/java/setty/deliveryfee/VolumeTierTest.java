package setty.deliveryfee;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VolumeTierTest {

    @DisplayName("부피가 300,000 세제곱센티미터이면 배송비는 10,000원이다")
    @Test
    void calculatesSmallDeliveryFeeAtUpperBoundary() {
        assertThat(VolumeTier.of(300L * 1_000 * 1).fixedFee()).isEqualTo(10_000);
    }

    @DisplayName("부피가 300,000 세제곱센티미터를 초과하면 배송비는 20,000원이다")
    @Test
    void calculatesMediumDeliveryFeeAboveSmallBoundary() {
        assertThat(VolumeTier.of(301L * 1_000 * 1).fixedFee()).isEqualTo(20_000);
    }

    @DisplayName("부피가 1,000,000 세제곱센티미터이면 배송비는 20,000원이다")
    @Test
    void calculatesMediumDeliveryFeeAtUpperBoundary() {
        assertThat(VolumeTier.of(1_000L * 1_000 * 1).fixedFee()).isEqualTo(20_000);
    }

    @DisplayName("부피가 1,000,000 세제곱센티미터를 초과하면 배송비는 30,000원이다")
    @Test
    void calculatesLargeDeliveryFeeAboveMediumBoundary() {
        assertThat(VolumeTier.of(1_000L * 1_000 * 2).fixedFee()).isEqualTo(30_000);
    }
}
