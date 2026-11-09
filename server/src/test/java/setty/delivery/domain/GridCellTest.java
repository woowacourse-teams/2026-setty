package setty.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class GridCellTest {

    // 기대값은 ~/osrm/phase8/grid_acc.py의 cell(x, y) = (int(x // DX), int(y // DY))로 계산했다.
    @Test
    void 거리_실험과_같은_식으로_칸을_나눈다() {
        assertThat(GridCell.locate(37.4985, 126.9520)).contains(new GridCell(44817, 16641));
        assertThat(GridCell.locate(37.5665, 126.9780)).contains(new GridCell(44826, 16672));
    }

    @Test
    void 칸_경계의_양쪽은_다른_칸이다() {
        final double longitudeStep = 0.25 / (111.32 * Math.cos(Math.toRadians(37.55)));
        final double latitudeStep = 0.25 / 110.95;
        final double edgeLongitude = 44817 * longitudeStep;
        final double edgeLatitude = 16641 * latitudeStep;

        assertThat(GridCell.locate(edgeLatitude + 1e-9, edgeLongitude + 1e-9)).contains(new GridCell(44817, 16641));
        assertThat(GridCell.locate(edgeLatitude + 1e-9, edgeLongitude - 1e-9)).contains(new GridCell(44816, 16641));
        assertThat(GridCell.locate(edgeLatitude - 1e-9, edgeLongitude + 1e-9)).contains(new GridCell(44817, 16640));
    }

    @Test
    void 칸_번호는_int_하나로_되돌릴_수_있다() {
        final GridCell cell = GridCell.locate(37.4985, 126.9520).orElseThrow();

        assertThat(cell.code()).isEqualTo(1_468_580_097);
        assertThat(GridCell.fromCode(cell.code())).isEqualTo(cell);
    }

    @Test
    void 이웃은_자기_칸을_포함한_9칸이다() {
        final GridCell cell = new GridCell(44817, 16641);

        assertThat(cell.withNeighborCodes())
                .hasSize(9)
                .doesNotHaveDuplicates()
                .contains(cell.code(),
                        new GridCell(44816, 16640).code(), new GridCell(44818, 16642).code(),
                        new GridCell(44816, 16642).code(), new GridCell(44818, 16640).code());
        assertThat(new HashSet<>(cell.withNeighborCodes()))
                .doesNotContain(new GridCell(44819, 16641).code(), new GridCell(44817, 16643).code());
    }

    @Test
    void 격자_원점_모서리에서는_밖의_이웃을_뺀다() {
        assertThat(new GridCell(0, 0).withNeighborCodes()).hasSize(4);
    }

    @Test
    void 좌표가_아니거나_격자_밖이면_칸이_없다() {
        assertThat(GridCell.locate(Double.NaN, 126.9)).isEmpty();
        assertThat(GridCell.locate(37.5, Double.POSITIVE_INFINITY)).isEmpty();
        assertThat(GridCell.locate(-33.8, 151.2)).isEmpty();
        assertThat(GridCell.locate(37.5, -122.4)).isEmpty();
        assertThat(GridCell.locate(80.0, 10.0)).isEmpty();
    }
}
