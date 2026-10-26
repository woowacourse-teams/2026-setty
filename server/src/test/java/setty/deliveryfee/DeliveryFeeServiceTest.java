package setty.deliveryfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import setty.deliveryfee.config.DeliveryFeeProperties;
import setty.deliveryfee.region.DistanceTable;
import setty.deliveryfee.region.SeoulDongs;
import setty.deliveryfee.region.SeoulZipZones;
import setty.global.exception.BusinessException;

class DeliveryFeeServiceTest {

    private final DeliveryFeeService deliveryFeeService = new DeliveryFeeService(
            new SeoulDongs(), new DistanceTable(), new SeoulZipZones(), Optional.empty(),
            new DeliveryFeeProperties(10_000, 5_000, 2, 3_000, 3_000));

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "3000, 0",
            "3001, 1",
            "6000, 1",
            "6001, 2",
            "24052, 8"
    })
    void 거리_단계는_3km까지_0이고_이후_3km마다_1씩_오른다(final int meters, final int expected) {
        assertThat(deliveryFeeService.distanceSteps(meters)).isEqualTo(expected);
    }

    @Test
    void 같은_동_안이면_부피_요금만_든다() {
        assertThat(deliveryFeeService.fee(0, 0, VolumeTier.SMALL)).isEqualTo(10_000);
        assertThat(deliveryFeeService.fee(0, 0, VolumeTier.MEDIUM)).isEqualTo(20_000);
        assertThat(deliveryFeeService.fee(0, 0, VolumeTier.LARGE)).isEqualTo(30_000);
    }

    @Test
    void 거리_단계와_부피_단계를_더해_배송비를_계산한다() {
        // 100번 동 → 300번 동은 24,052m라 거리 8단계
        assertThat(deliveryFeeService.steps(100, 300, VolumeTier.SMALL)).isEqualTo(8);
        assertThat(deliveryFeeService.fee(100, 300, VolumeTier.SMALL)).isEqualTo(50_000);
        assertThat(deliveryFeeService.steps(100, 300, VolumeTier.LARGE)).isEqualTo(12);
        assertThat(deliveryFeeService.fee(100, 300, VolumeTier.LARGE)).isEqualTo(70_000);
    }

    // Q3에서 고른 거리표 조회 방식과 같은 거리표·단계 규칙으로 실험 때 구한 통과 동 수
    @ParameterizedTest
    @CsvSource({
            "0, 2, 114, 9, 0",
            "0, 4, 302, 114, 9",
            "149, 2, 34, 2, 0",
            "342, 8, 425, 382, 249"
    })
    void 예산_안에_드는_판매자_동을_부피_구간별로_찾는다(
            final int buyerDong, final int budgetSteps, final int small, final int medium, final int large
    ) {
        assertThat(deliveryFeeService.passingSellerDongs(buyerDong, VolumeTier.SMALL, budgetSteps)).hasSize(small);
        assertThat(deliveryFeeService.passingSellerDongs(buyerDong, VolumeTier.MEDIUM, budgetSteps)).hasSize(medium);
        assertThat(deliveryFeeService.passingSellerDongs(buyerDong, VolumeTier.LARGE, budgetSteps)).hasSize(large);
    }

    @Test
    void 통과한_판매자_동은_모두_예산_안이고_빠진_동은_모두_예산_밖이다() {
        final int buyerDong = 342;
        final int budgetSteps = 4;

        final var passing = deliveryFeeService.passingSellerDongs(buyerDong, VolumeTier.MEDIUM, budgetSteps);

        for (int sellerDong = 0; sellerDong < 427; sellerDong++) {
            final boolean withinBudget = deliveryFeeService.steps(sellerDong, buyerDong, VolumeTier.MEDIUM) <= budgetSteps;
            assertThat(passing.contains(sellerDong)).isEqualTo(withinBudget);
        }
    }

    @Test
    void 없는_행정동이면_계산할_수_없다() {
        assertThatThrownBy(() -> deliveryFeeService.fee(-1, 0, VolumeTier.SMALL)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> deliveryFeeService.fee(0, 427, VolumeTier.SMALL)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> deliveryFeeService.passingSellerDongs(427, VolumeTier.SMALL, 4))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 거리_규칙을_바꾸면_거리_단계도_바뀐다() {
        final DeliveryFeeService fiveKilometerRule = new DeliveryFeeService(
                new SeoulDongs(), new DistanceTable(), new SeoulZipZones(), Optional.empty(),
                new DeliveryFeeProperties(10_000, 5_000, 2, 5_000, 5_000));

        // 24,052m: 무료 5km를 넘은 19,052m를 5km 단계로 나누면 4단계
        assertThat(fiveKilometerRule.steps(100, 300, VolumeTier.SMALL)).isEqualTo(4);
    }

    @Test
    void 단계당_금액이나_단계_거리가_0_이하면_설정할_수_없다() {
        assertThatThrownBy(() -> new DeliveryFeeProperties(10_000, 0, 2, 3_000, 3_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryFeeProperties(10_000, 5_000, 2, 3_000, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 예산_안에_드는_판매자_동을_부피_구간별로_준다() {
        final Dong buyer = deliveryFeeService.findDong("1111053000").orElseThrow();

        final var within = deliveryFeeService.sellerDongsWithinFee(buyer, 15_000);

        assertThat(within).containsOnlyKeys(VolumeTier.SMALL);
        assertThat(within.get(VolumeTier.SMALL)).contains(buyer)
                .allSatisfy(seller -> assertThat(deliveryFeeService.fee(seller.index(), buyer.index(), VolumeTier.SMALL))
                        .isLessThanOrEqualTo(15_000));
        assertThat(within.get(VolumeTier.SMALL)).hasSize(
                deliveryFeeService.passingSellerDongs(buyer.index(), VolumeTier.SMALL, 1).size());
    }

    @Test
    void 기본_배송비보다_작은_예산이면_판매자_동이_없다() {
        final Dong buyer = deliveryFeeService.findDong("1111053000").orElseThrow();

        assertThat(deliveryFeeService.sellerDongsWithinFee(buyer, 9_999)).isEmpty();
    }
}
