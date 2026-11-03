package setty.delivery.application;

import java.time.Instant;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/** DB의 마이크로초 정밀도를 유지한 정렬 시각과 배송 ID. */
public record DeliveryCursor(Instant time, long id) {

    public static DeliveryCursor parse(final String value) {
        if (value == null) {
            return null;
        }
        final String[] parts = value.split("_", -1);
        try {
            if (parts.length != 2) {
                throw new NumberFormatException(value);
            }
            final long micros = Long.parseLong(parts[0]);
            final long id = Long.parseLong(parts[1]);
            if (micros < 0 || id <= 0) {
                throw new NumberFormatException(value);
            }
            return new DeliveryCursor(Instant.ofEpochSecond(micros / 1_000_000, micros % 1_000_000 * 1_000), id);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public String encode() {
        return (time.getEpochSecond() * 1_000_000 + time.getNano() / 1_000) + "_" + id;
    }
}
