package setty.deliveryfee.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SeoulZipZonesTest {

    private final SeoulZipZones zones = new SeoulZipZones();

    @Test
    void 서울_우편번호_5634개를_번호순으로_읽는다() {
        assertThat(zones.size()).isEqualTo(5_634);
        assertThat(zones.zipCodeOf(0)).hasValue("01000");
        assertThat(zones.zipCodeOf(5_633)).hasValue("08866");
    }

    @Test
    void 우편번호와_구역_번호를_서로_바꾼다() {
        final int zone = zones.zoneOf("03186").orElseThrow();

        assertThat(zones.zipCodeOf(zone)).hasValue("03186");
    }

    @Test
    void 서울_밖이거나_없는_우편번호는_구역이_없다() {
        assertThat(zones.zoneOf("13529")).isEmpty();
        assertThat(zones.zoneOf("00000")).isEmpty();
        assertThat(zones.zoneOf(null)).isEmpty();
        assertThat(zones.zipCodeOf(-1)).isEmpty();
        assertThat(zones.zipCodeOf(5_634)).isEmpty();
    }

    @Test
    void 번호순이_아니면_읽지_않는다() {
        assertThatThrownBy(() -> new SeoulZipZones(List.of("01001", "01000")))
                .isInstanceOf(IllegalStateException.class);
    }
}
