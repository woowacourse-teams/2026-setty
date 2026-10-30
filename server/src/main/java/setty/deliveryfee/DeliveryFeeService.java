package setty.deliveryfee;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import setty.deliveryfee.config.DeliveryFeeProperties;
import setty.deliveryfee.region.DistanceTable;
import setty.deliveryfee.region.SeoulDongs;
import setty.deliveryfee.region.SeoulZipZones;
import setty.deliveryfee.region.ZipDistanceTable;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
public class DeliveryFeeService {

    private final SeoulDongs seoulDongs;
    private final DistanceTable distanceTable;
    private final SeoulZipZones zipZones;
    private final ZipDistanceTable zipDistanceTable;
    private final DeliveryFeeProperties properties;

    // 우편번호 거리표는 zone-mode가 zip이고 표를 얻었을 때만 쓴다. 아니면 모든 계산이 행정동으로 간다.
    public DeliveryFeeService(
            final SeoulDongs seoulDongs,
            final DistanceTable distanceTable,
            final SeoulZipZones zipZones,
            final Optional<ZipDistanceTable> zipDistanceTable,
            final DeliveryFeeProperties properties
    ) {
        this.seoulDongs = seoulDongs;
        this.distanceTable = distanceTable;
        this.zipZones = zipZones;
        this.zipDistanceTable = zipDistanceTable.filter(ZipDistanceTable::available).orElse(null);
        this.properties = properties;
        if (this.zipDistanceTable != null && this.zipDistanceTable.size() != zipZones.size()) {
            throw new IllegalStateException("우편번호 거리표 크기(" + this.zipDistanceTable.size()
                    + ")가 구역 수(" + zipZones.size() + ")와 다릅니다");
        }
    }

    public List<Dong> dongs() {
        return seoulDongs.all();
    }

    public Optional<Dong> findDong(final String code) {
        return seoulDongs.findByCode(code);
    }

    public Optional<Dong> locate(final double latitude, final double longitude) {
        return seoulDongs.locate(latitude, longitude);
    }

    public int steps(final int sellerDong, final int buyerDong, final VolumeTier volumeTier) {
        validate(sellerDong);
        validate(buyerDong);
        return volumeTier.ordinal() * properties.volumeTierSteps()
                + distanceSteps(distanceTable.meters(sellerDong, buyerDong));
    }

    public int fee(final int sellerDong, final int buyerDong, final VolumeTier volumeTier) {
        return properties.baseFee() + steps(sellerDong, buyerDong, volumeTier) * properties.stepPrice();
    }

    public List<Integer> passingSellerDongs(final int buyerDong, final VolumeTier volumeTier, final int budgetSteps) {
        validate(buyerDong);
        final List<Integer> dongs = new ArrayList<>();
        for (int sellerDong = 0; sellerDong < distanceTable.size(); sellerDong++) {
            if (steps(sellerDong, buyerDong, volumeTier) <= budgetSteps) {
                dongs.add(sellerDong);
            }
        }
        return List.copyOf(dongs);
    }

    public Map<VolumeTier, List<Dong>> sellerDongsWithinFee(final Dong buyerDong, final int maxFee) {
        final Map<VolumeTier, List<Dong>> result = new EnumMap<>(VolumeTier.class);
        if (maxFee < properties.baseFee()) {
            return result;
        }
        final int budgetSteps = (maxFee - properties.baseFee()) / properties.stepPrice();
        final List<Dong> dongs = seoulDongs.all();
        for (final VolumeTier volumeTier : VolumeTier.values()) {
            final List<Dong> sellerDongs = passingSellerDongs(buyerDong.index(), volumeTier, budgetSteps).stream()
                    .map(dongs::get)
                    .toList();
            if (!sellerDongs.isEmpty()) {
                result.put(volumeTier, sellerDongs);
            }
        }
        return result;
    }

    // 매물 상세와 같은 규칙. 우편번호 구역을 둘 다 알면 구역 표, 아니면 행정동 표, 구매자 동을 모르면 기본값을 쓴다.
    public int feeOrDefault(
            final String sellerDongCode,
            final Integer sellerZipZone,
            final String buyerDongCode,
            final String buyerZipCode,
            final VolumeTier volumeTier,
            final int defaultFee
    ) {
        if (volumeTier == null) {
            return defaultFee;
        }
        final Integer buyerZipZone = buyerZipCode == null ? null : zipZoneOf(buyerZipCode).orElse(null);
        return zipFee(sellerZipZone, buyerZipZone, volumeTier)
                .orElseGet(() -> dongFeeOrDefault(sellerDongCode, buyerDongCode, volumeTier, defaultFee));
    }

    private int dongFeeOrDefault(
            final String sellerDongCode,
            final String buyerDongCode,
            final VolumeTier volumeTier,
            final int defaultFee
    ) {
        if (sellerDongCode == null || buyerDongCode == null) {
            return defaultFee;
        }
        final Optional<Dong> seller = findDong(sellerDongCode);
        final Optional<Dong> buyer = findDong(buyerDongCode);
        if (seller.isEmpty() || buyer.isEmpty()) {
            return defaultFee;
        }
        return fee(seller.get().index(), buyer.get().index(), volumeTier);
    }

    public Optional<Integer> zipZoneOf(final String zipCode) {
        return zipZones.zoneOf(zipCode);
    }

    public Optional<String> zipCodeOf(final int zipZone) {
        return zipZones.zipCodeOf(zipZone);
    }

    public boolean zipEnabled() {
        return zipDistanceTable != null;
    }

    // 두 구역을 모두 알고 우편번호 거리표가 켜져 있을 때만 값이 있다. 없으면 호출한 쪽이 행정동으로 계산한다.
    public Optional<Integer> zipFee(final Integer sellerZone, final Integer buyerZone, final VolumeTier volumeTier) {
        if (!zipEnabled() || sellerZone == null || buyerZone == null
                || sellerZone < 0 || sellerZone >= zipZones.size() || buyerZone < 0 || buyerZone >= zipZones.size()) {
            return Optional.empty();
        }
        return Optional.of(properties.baseFee() + zipSteps(sellerZone, buyerZone, volumeTier) * properties.stepPrice());
    }

    // 예산 안에 드는 판매자 구역을 번호순으로 이어진 범위로 묶는다. 구매자 행 하나만 읽는다.
    public Map<VolumeTier, List<ZoneRange>> sellerZipZonesWithinFee(final int buyerZone, final int maxFee) {
        if (!zipEnabled()) {
            throw new IllegalStateException("우편번호 거리표가 꺼져 있습니다");
        }
        validateZipZone(buyerZone);
        final Map<VolumeTier, List<ZoneRange>> result = new EnumMap<>(VolumeTier.class);
        if (maxFee < properties.baseFee()) {
            return result;
        }
        final int budgetSteps = (maxFee - properties.baseFee()) / properties.stepPrice();
        for (final VolumeTier volumeTier : VolumeTier.values()) {
            final List<ZoneRange> ranges = new ArrayList<>();
            int start = -1;
            for (int sellerZone = 0; sellerZone < zipZones.size(); sellerZone++) {
                final boolean passes = zipSteps(sellerZone, buyerZone, volumeTier) <= budgetSteps;
                if (passes && start < 0) {
                    start = sellerZone;
                } else if (!passes && start >= 0) {
                    ranges.add(new ZoneRange(start, sellerZone - 1));
                    start = -1;
                }
            }
            if (start >= 0) {
                ranges.add(new ZoneRange(start, zipZones.size() - 1));
            }
            if (!ranges.isEmpty()) {
                result.put(volumeTier, List.copyOf(ranges));
            }
        }
        return result;
    }

    private int zipSteps(final int sellerZone, final int buyerZone, final VolumeTier volumeTier) {
        return volumeTier.ordinal() * properties.volumeTierSteps()
                + distanceSteps(zipDistanceTable.meters(sellerZone, buyerZone));
    }

    private void validateZipZone(final int zone) {
        if (zone < 0 || zone >= zipZones.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    int distanceSteps(final int meters) {
        if (meters <= properties.freeDistanceMeters()) {
            return 0;
        }
        final int over = meters - properties.freeDistanceMeters();
        return (over + properties.stepDistanceMeters() - 1) / properties.stepDistanceMeters();
    }

    private void validate(final int dong) {
        if (dong < 0 || dong >= distanceTable.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
