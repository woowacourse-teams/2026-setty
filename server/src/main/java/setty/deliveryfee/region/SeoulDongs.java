package setty.deliveryfee.region;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import setty.deliveryfee.Dong;

// 행정동 index는 거리표의 행·열 순서와 같아야 한다(두 리소스를 같은 코드순으로 만들었다).
@Component
public class SeoulDongs {

    private static final String DONGS = "deliveryfee/seoul-dongs.csv";
    private static final String BOUNDARIES = "deliveryfee/seoul-dong-boundaries.txt";

    private final List<Dong> dongs = new ArrayList<>();
    private final Map<String, Dong> dongsByCode = new HashMap<>();
    private final List<List<Polygon>> polygons = new ArrayList<>();

    public SeoulDongs() {
        for (final String line : readLines(DONGS)) {
            final String[] fields = line.split(",");
            dongs.add(new Dong(Integer.parseInt(fields[0]), fields[1], fields[2], fields[3]));
            polygons.add(new ArrayList<>());
        }
        dongs.forEach(dong -> dongsByCode.put(dong.code(), dong));
        loadBoundaries();
    }

    public List<Dong> all() {
        return List.copyOf(dongs);
    }

    public Optional<Dong> findByCode(final String code) {
        return Optional.ofNullable(dongsByCode.get(code));
    }

    public Optional<Dong> locate(final double latitude, final double longitude) {
        for (int index = 0; index < dongs.size(); index++) {
            for (final Polygon polygon : polygons.get(index)) {
                if (polygon.contains(longitude, latitude)) {
                    return Optional.of(dongs.get(index));
                }
            }
        }
        return Optional.empty();
    }

    // 한 줄이 고리 하나다: "동 index, 폴리곤 번호, 고리 번호(0은 바깥, 나머지는 구멍), 경도,위도 …"
    private void loadBoundaries() {
        for (final String line : readLines(BOUNDARIES)) {
            final String[] fields = line.split(" ");
            final List<Polygon> dongPolygons = polygons.get(Integer.parseInt(fields[0]));
            final int polygonNumber = Integer.parseInt(fields[1]);
            final Ring ring = Ring.parse(fields, 3);
            if (Integer.parseInt(fields[2]) == 0) {
                dongPolygons.add(new Polygon(ring, new ArrayList<>()));
            } else {
                dongPolygons.get(polygonNumber).holes().add(ring);
            }
        }
    }

    private static List<String> readLines(final String resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank()).toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Polygon(Ring outer, List<Ring> holes) {

        boolean contains(final double x, final double y) {
            if (!outer.contains(x, y)) {
                return false;
            }
            return holes.stream().noneMatch(hole -> hole.contains(x, y));
        }
    }

    private record Ring(double[] xs, double[] ys, double minX, double minY, double maxX, double maxY) {

        static Ring parse(final String[] fields, final int from) {
            final int count = fields.length - from;
            final double[] xs = new double[count];
            final double[] ys = new double[count];
            for (int i = 0; i < count; i++) {
                final String[] point = fields[from + i].split(",");
                xs[i] = Double.parseDouble(point[0]);
                ys[i] = Double.parseDouble(point[1]);
            }
            return new Ring(xs, ys, min(xs), min(ys), max(xs), max(ys));
        }

        boolean contains(final double x, final double y) {
            if (x < minX || x > maxX || y < minY || y > maxY) {
                return false;
            }
            boolean inside = false;
            for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                    inside = !inside;
                }
            }
            return inside;
        }

        private static double min(final double[] values) {
            double min = Double.MAX_VALUE;
            for (final double value : values) {
                min = Math.min(min, value);
            }
            return min;
        }

        private static double max(final double[] values) {
            double max = -Double.MAX_VALUE;
            for (final double value : values) {
                max = Math.max(max, value);
            }
            return max;
        }
    }
}
