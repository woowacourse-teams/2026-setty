package setty.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class SeoulDateTime {
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private SeoulDateTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(SEOUL_ZONE_ID);
    }

    public static OffsetDateTime toOffsetDateTime(final LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.atZone(SEOUL_ZONE_ID).toOffsetDateTime();
    }
}
