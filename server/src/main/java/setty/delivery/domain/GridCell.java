package setty.delivery.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 250m 격자 칸. 칸 경계는 거리 실험(~/osrm/phase8/grid_acc.py)과 같게 경위도 0을 원점으로,
 * 위도 37.55도에서 가로·세로 250m가 되도록 나눈다.
 */
public record GridCell(int column, int row) {

    private static final double LATITUDE_ORIGIN = 37.55;
    private static final double SIZE_KM = 0.25;
    private static final double LONGITUDE_STEP = SIZE_KM / (111.32 * Math.cos(Math.toRadians(LATITUDE_ORIGIN)));
    private static final double LATITUDE_STEP = SIZE_KM / 110.95;
    // 열 16비트 · 행 15비트를 int 하나에 담는다. 경도 0~185도, 위도 0~73도까지 표현된다.
    private static final int ROWS = 1 << 15;
    private static final int COLUMNS = 1 << 16;

    public GridCell {
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            throw new IllegalArgumentException("격자 범위를 벗어난 칸입니다.");
        }
    }

    public static Optional<GridCell> locate(final double latitude, final double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return Optional.empty();
        }
        final double column = Math.floor(longitude / LONGITUDE_STEP);
        final double row = Math.floor(latitude / LATITUDE_STEP);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            return Optional.empty();
        }
        return Optional.of(new GridCell((int) column, (int) row));
    }

    public static GridCell fromCode(final int code) {
        return new GridCell(code / ROWS, code % ROWS);
    }

    public int code() {
        return column * ROWS + row;
    }

    /** 자기 칸과 이웃 8칸의 번호. 격자 가장자리 밖은 뺀다. */
    public List<Integer> withNeighborCodes() {
        final List<Integer> codes = new ArrayList<>(9);
        for (int dc = -1; dc <= 1; dc++) {
            for (int dr = -1; dr <= 1; dr++) {
                final int c = column + dc;
                final int r = row + dr;
                if (c >= 0 && c < COLUMNS && r >= 0 && r < ROWS) {
                    codes.add(c * ROWS + r);
                }
            }
        }
        return codes;
    }
}
