package setty.platform.listing.application;

import java.time.Instant;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

// 클라이언트와는 "등록 시각(epoch 마이크로초)_매물 id" 문자열로 주고받는다. created_at이 마이크로초까지라 밀리초로 줄이면 경계가 어긋난다.
public record ListingCursor(Instant createdAt, Long id) {

    private static final String SEPARATOR = "_";

    public static ListingCursor parse(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(SEPARATOR, -1);
        try {
            if (parts.length != 2) {
                throw new NumberFormatException(value);
            }
            long micros = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (micros < 0 || id <= 0) {
                throw new NumberFormatException(value);
            }
            return new ListingCursor(Instant.ofEpochSecond(micros / 1_000_000, micros % 1_000_000 * 1_000), id);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public String encode() {
        long micros = createdAt.getEpochSecond() * 1_000_000 + createdAt.getNano() / 1_000;
        return micros + SEPARATOR + id;
    }
}
