package setty.deliveryfee;

import java.util.List;
import java.util.Map;

// 행정동 모드면 dongCodes, 우편번호 모드면 zipZones 한쪽만 값이 있다.
public record SellersWithinFee(
        Map<VolumeTier, List<String>> dongCodes,
        Map<VolumeTier, List<ZoneRange>> zipZones
) {

    static SellersWithinFee dongs(final Map<VolumeTier, List<String>> dongCodes) {
        return new SellersWithinFee(dongCodes, null);
    }

    static SellersWithinFee zipZones(final Map<VolumeTier, List<ZoneRange>> zipZones) {
        return new SellersWithinFee(null, zipZones);
    }

    public boolean isEmpty() {
        return dongCodes != null ? dongCodes.isEmpty() : zipZones.isEmpty();
    }
}
