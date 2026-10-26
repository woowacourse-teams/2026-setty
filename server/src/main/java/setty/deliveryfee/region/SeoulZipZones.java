package setty.deliveryfee.region;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// 구역 번호는 우편번호를 번호순으로 늘어놓은 순서다. 이웃 구역끼리 번호가 이어져 검색 조건을 범위로 묶을 수 있다.
// 우편번호 거리표의 행·열 순서와 같아야 한다.
@Component
public class SeoulZipZones {

    private static final String ZONES = "deliveryfee/seoul-zip-zones.csv";

    private final String[] zipCodes;

    public SeoulZipZones() {
        this(readZipCodes());
    }

    public SeoulZipZones(final List<String> zipCodes) {
        this.zipCodes = zipCodes.toArray(String[]::new);
        for (int i = 1; i < this.zipCodes.length; i++) {
            if (this.zipCodes[i - 1].compareTo(this.zipCodes[i]) >= 0) {
                throw new IllegalStateException("우편번호 구역이 번호순이 아닙니다: " + this.zipCodes[i]);
            }
        }
    }

    public Optional<Integer> zoneOf(final String zipCode) {
        if (zipCode == null) {
            return Optional.empty();
        }
        final int zone = Arrays.binarySearch(zipCodes, zipCode);
        return zone < 0 ? Optional.empty() : Optional.of(zone);
    }

    public Optional<String> zipCodeOf(final int zone) {
        if (zone < 0 || zone >= zipCodes.length) {
            return Optional.empty();
        }
        return Optional.of(zipCodes[zone]);
    }

    public int size() {
        return zipCodes.length;
    }

    private static List<String> readZipCodes() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(ZONES).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank())
                    .map(line -> line.split(",")[1].trim())
                    .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
