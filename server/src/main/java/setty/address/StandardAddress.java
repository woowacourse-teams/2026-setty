package setty.address;

import java.math.BigDecimal;

public record StandardAddress(
        String managementNumber,
        String roadAddress,
        String detail,
        String zipCode,
        String dongCode,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
