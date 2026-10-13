package setty.deliveryfee.region;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import setty.deliveryfee.Dong;

class SeoulDongsTest {

    private final SeoulDongs seoulDongs = new SeoulDongs();

    @ParameterizedTest
    @CsvSource({
            "37.5714, 126.9768, 1, 1111053000, 사직동, 종로구",
            "37.4979, 127.0276, 342, 1165052000, 서초2동, 서초구",
            "37.5215, 126.9246, 289, 1156054000, 여의동, 영등포구",
            "37.5133, 127.1000, 404, 1171068000, 잠실3동, 송파구",
            "37.6850, 127.0450, 149, 1132052100, 도봉1동, 도봉구"
    })
    void 좌표가_속한_행정동을_찾는다(
            final double latitude, final double longitude,
            final int index, final String code, final String name, final String district
    ) {
        assertThat(seoulDongs.locate(latitude, longitude)).contains(new Dong(index, code, name, district));
    }

    @ParameterizedTest
    @CsvSource({
            "37.3830, 127.1150",
            "37.5070, 126.7220"
    })
    void 서울_밖이면_행정동이_없다(final double latitude, final double longitude) {
        assertThat(seoulDongs.locate(latitude, longitude)).isEmpty();
    }

    @Test
    void 동_경계를_사이에_둔_두_점은_서로_다른_동으로_판정한다() {
        // 청운효자동(0)과 삼청동(2)이 맞닿은 변의 가운데에서 양쪽으로 약 5m씩 떨어진 점
        assertThat(seoulDongs.locate(37.5929453304967, 126.9736633201303).map(Dong::index)).contains(2);
        assertThat(seoulDongs.locate(37.592937811777894, 126.97356360318656).map(Dong::index)).contains(0);
        assertThat(seoulDongs.locate(37.59116790615077, 126.97470735329664).map(Dong::index)).contains(2);
        assertThat(seoulDongs.locate(37.59111024708321, 126.97462564998013).map(Dong::index)).contains(0);
    }
}
