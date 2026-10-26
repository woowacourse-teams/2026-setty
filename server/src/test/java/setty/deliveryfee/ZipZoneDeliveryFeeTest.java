package setty.deliveryfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import setty.deliveryfee.config.DeliveryFeeProperties;
import setty.deliveryfee.region.DistanceTable;
import setty.deliveryfee.region.SeoulDongs;
import setty.deliveryfee.region.SeoulZipZones;
import setty.deliveryfee.region.ZipDistanceTable;
import setty.global.exception.BusinessException;

class ZipZoneDeliveryFeeTest {

    private static final DeliveryFeeProperties PROPERTIES = new DeliveryFeeProperties(10_000, 5_000, 2, 3_000, 3_000);
    private static final int ZONES = 40;

    // 구매자 0번 행의 판매자 거리(km). 3km 이하 통과가 0~2, 13~14, 20, 39번으로 끊겨 있다.
    private static final double[] FROM_BUYER_ZERO = {
            0.2, 1, 3, 3.1, 5, 6, 6.1, 7, 9, 9.1, 12, 15, 20, 2, 2.9, 4, 30, 40, 50, 60, 1.5, 4, 4, 4, 4, 4, 4, 4, 4, 4,
            4, 4, 4, 4, 4, 4, 4, 4, 4, 0.5
    };

    @TempDir
    Path dir;

    private DeliveryFeeService service;

    @BeforeEach
    void setUp() throws IOException {
        service = serviceWith(writeTable(ZONES));
    }

    @Test
    void 우편번호_거리표가_켜져_있으면_두_구역으로_배송비를_계산한다() {
        // 5km: 무료 3km를 넘어 1단계
        assertThat(service.zipFee(4, 0, VolumeTier.SMALL)).hasValue(15_000);
        assertThat(service.zipFee(4, 0, VolumeTier.LARGE)).hasValue(35_000);
        // 판매자 3 → 구매자 0은 3.1km, 반대 방향(판매자 0 → 구매자 3)은 3km로 표에 따로 있다
        assertThat(service.zipFee(3, 0, VolumeTier.SMALL)).hasValue(15_000);
        assertThat(service.zipFee(0, 3, VolumeTier.SMALL)).hasValue(10_000);
    }

    @Test
    void 구역을_하나라도_모르면_우편번호로_계산하지_않는다() {
        assertThat(service.zipFee(null, 0, VolumeTier.SMALL)).isEmpty();
        assertThat(service.zipFee(4, null, VolumeTier.SMALL)).isEmpty();
        assertThat(service.zipFee(ZONES, 0, VolumeTier.SMALL)).isEmpty();
    }

    @Test
    void 거리표가_꺼져_있으면_우편번호로_계산하지_않는다() {
        final DeliveryFeeService off = new DeliveryFeeService(
                new SeoulDongs(), new DistanceTable(), zones(ZONES), Optional.empty(), PROPERTIES);

        assertThat(off.zipEnabled()).isFalse();
        assertThat(off.zipFee(4, 0, VolumeTier.SMALL)).isEmpty();
        assertThatThrownBy(() -> off.sellerZipZonesWithinFee(0, 20_000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 이어진_통과_구역을_범위로_묶는다() {
        final Map<VolumeTier, List<ZoneRange>> within = service.sellerZipZonesWithinFee(0, 10_000);

        assertThat(within).containsOnlyKeys(VolumeTier.SMALL);
        assertThat(within.get(VolumeTier.SMALL)).containsExactly(
                new ZoneRange(0, 2), new ZoneRange(13, 14), new ZoneRange(20, 20), new ZoneRange(39, 39));
    }

    @ParameterizedTest
    @ValueSource(ints = {9_999, 10_000, 15_000, 20_000, 30_000, 45_000, 80_000})
    void 범위에_든_구역은_모두_예산_안이고_빠진_구역은_모두_예산_밖이다(final int maxFee) {
        final Map<VolumeTier, List<ZoneRange>> within = service.sellerZipZonesWithinFee(0, maxFee);

        for (final VolumeTier volumeTier : VolumeTier.values()) {
            final List<Integer> passing = new ArrayList<>();
            within.getOrDefault(volumeTier, List.of())
                    .forEach(range -> IntStream.rangeClosed(range.from(), range.to()).forEach(passing::add));
            for (int seller = 0; seller < ZONES; seller++) {
                final boolean withinBudget = service.zipFee(seller, 0, volumeTier).orElseThrow() <= maxFee;
                assertThat(passing.contains(seller)).as("판매자 %d, %s", seller, volumeTier).isEqualTo(withinBudget);
            }
        }
    }

    @Test
    void 없는_구매자_구역이면_찾을_수_없다() {
        assertThatThrownBy(() -> service.sellerZipZonesWithinFee(ZONES, 20_000)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 거리표_크기가_구역_수와_다르면_시작하지_않는다() throws IOException {
        assertThatThrownBy(() -> new DeliveryFeeService(new SeoulDongs(), new DistanceTable(), zones(ZONES + 1),
                Optional.of(ZipDistanceTable.of(writeTable(ZONES))), PROPERTIES))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 구역_번호와_우편번호를_서로_바꾼다() {
        assertThat(service.zipZoneOf("01005")).hasValue(5);
        assertThat(service.zipCodeOf(5)).hasValue("01005");
        assertThat(service.zipZoneOf("99999")).isEmpty();
    }

    private DeliveryFeeService serviceWith(final Path table) {
        return new DeliveryFeeService(new SeoulDongs(), new DistanceTable(), zones(ZONES),
                Optional.of(ZipDistanceTable.of(table)), PROPERTIES);
    }

    private static SeoulZipZones zones(final int count) {
        return new SeoulZipZones(IntStream.range(0, count).mapToObj(i -> String.format("%05d", 1_000 + i)).toList());
    }

    // 구매자 0번 행만 FROM_BUYER_ZERO로 채우고, 나머지는 번호 차이 × 1km
    private Path writeTable(final int size) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(size * size * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int buyer = 0; buyer < size; buyer++) {
            for (int seller = 0; seller < size; seller++) {
                final double km = buyer == 0 && seller < FROM_BUYER_ZERO.length
                        ? FROM_BUYER_ZERO[seller]
                        : Math.abs(buyer - seller);
                buffer.putShort((short) Math.round(km * 100));
            }
        }
        return Files.write(dir.resolve("zip-" + size + ".bin"), buffer.array());
    }
}
