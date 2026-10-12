package setty.deliveryfee.region;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DistanceTableTest {

    private final DistanceTable distanceTable = new DistanceTable();

    @Test
    void 서울_행정동_427개의_거리표를_읽는다() {
        assertThat(distanceTable.size()).isEqualTo(427);
    }

    @Test
    void 판매자_동에서_구매자_동까지의_도로_거리를_미터로_돌려준다() {
        assertThat(distanceTable.meters(100, 300)).isEqualTo(24_052);
        assertThat(distanceTable.meters(426, 0)).isEqualTo(18_212);
    }

    @Test
    void 방향마다_거리가_다를_수_있다() {
        assertThat(distanceTable.meters(0, 1)).isEqualTo(975);
        assertThat(distanceTable.meters(1, 0)).isEqualTo(713);
    }

    @Test
    void 같은_동은_동_안_거리를_돌려준다() {
        assertThat(distanceTable.meters(0, 0)).isEqualTo(173);
    }
}
