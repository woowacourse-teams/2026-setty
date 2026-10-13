package setty.deliveryfee;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import setty.deliveryfee.config.DeliveryFeeProperties;
import setty.deliveryfee.region.DistanceTable;
import setty.deliveryfee.region.SeoulDongs;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class DeliveryFeeService {

    private final SeoulDongs seoulDongs;
    private final DistanceTable distanceTable;
    private final DeliveryFeeProperties properties;

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
