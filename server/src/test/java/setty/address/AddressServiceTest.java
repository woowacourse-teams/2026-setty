package setty.address;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import setty.address.AddressResolution.Status;
import setty.support.MySqlIntegrationTestSupport;

// 2026-09 도로명주소 DB에서 뽑은 실제 주소 표본(좌표 없음). server/scripts/address/build_reference.py 출력과 칸이 같다.
@SpringBootTest
class AddressServiceTest extends MySqlIntegrationTestSupport {

    private static final String SAMPLE = "address/address-reference-sample.csv";
    private static final String CITY_HALL = "1114010300100310000019224";
    private static final String SEJONG_175 = "1111011900100810003027269";
    private static final String SEJONG_175_UNDERGROUND = "1111011900100010068000001";

    @Autowired
    private AddressService addressService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void loadSample() {
        cleanUp();
        jdbcTemplate.batchUpdate("""
                INSERT INTO address_reference (mgmt_no, road_code, road_name, underground, building_main, building_sub,
                    sigungu, legal_dong, jibun_mountain, jibun_main, jibun_sub, zip_code, dong_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, sample().stream().map(row -> (Object[]) row).toList());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM address_reference");
    }

    @ParameterizedTest
    @MethodSource("aboveGroundRows")
    void 구와_도로명_건물번호로_표본_주소를_모두_맞춘다(final String[] row) {
        final String number = row[5].equals("0") ? row[4] : row[4] + "-" + row[5];

        final AddressResolution resolution = addressService.resolve("서울특별시 " + row[6] + " " + row[2] + " " + number);

        assertThat(resolution.status()).isEqualTo(Status.MATCHED);
        assertThat(resolution.address().managementNumber()).isEqualTo(row[0]);
        assertThat(resolution.address().roadAddress()).isEqualTo("서울특별시 " + row[6] + " " + row[2] + " " + number);
        assertThat(resolution.address().zipCode()).isEqualTo(row[11]);
        assertThat(resolution.address().dongCode()).isEqualTo(row[12]);
    }

    @ParameterizedTest
    @MethodSource("uniqueJibunRows")
    void 법정동과_지번으로_표본_주소를_모두_맞춘다(final String[] row) {
        final String number = (row[8].equals("1") ? "산" : "") + (row[10].equals("0") ? row[9] : row[9] + "-" + row[10]);

        final AddressResolution resolution = addressService.resolve(row[6] + " " + row[7] + " " + number + "번지");

        assertThat(resolution.status()).isEqualTo(Status.MATCHED);
        assertThat(resolution.address().managementNumber()).isEqualTo(row[0]);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "서울특별시 중구 세종대로 110",
            "서울시 중구 세종대로 110",
            "서울 중구 세종대로110",
            "서울특별시중구 세종대로 110 (태평로1가)",
            "  중구   세종대로 １１０ ",
            "세종대로 110번"
    })
    void 서울_표기와_띄어쓰기_괄호_차이를_정리한다(final String freeText) {
        final AddressResolution resolution = addressService.resolve(freeText);

        assertThat(resolution.status()).isEqualTo(Status.MATCHED);
        assertThat(resolution.address()).isEqualTo(new StandardAddress(
                CITY_HALL, "서울특별시 중구 세종대로 110", null, "04524", "1114055000", null, null));
    }

    @Test
    void 건물번호_뒤의_글자는_상세주소로_남긴다() {
        assertThat(addressService.resolve("서울특별시 송파구 올림픽로 300, 101동 1203호").address().detail())
                .isEqualTo("101동 1203호");
        assertThat(addressService.resolve("테헤란로 152 (역삼동) 강남파이낸스센터 21층").address().detail())
                .isEqualTo("강남파이낸스센터 21층");
    }

    @Test
    void 띄어_쓴_길_번호와_부번을_붙여_읽는다() {
        assertThat(addressService.resolve("광진구 긴고랑로 29길 57").address().managementNumber())
                .isEqualTo("1121510100100350020023022");
        assertThat(addressService.resolve("종로구 경희궁1가길 17 - 1").address().roadAddress())
                .isEqualTo("서울특별시 종로구 경희궁1가길 17-1");
    }

    @Test
    void 숫자로_시작하는_도로명도_읽는다() {
        assertThat(addressService.resolve("서울특별시 강북구 4.19로 25").address().managementNumber())
                .isEqualTo("1130510300105680102004138");
        assertThat(addressService.resolve("서울시 63로 10").address().managementNumber())
                .isEqualTo("1156011000100620000031477");
    }

    @Test
    void 한글이_붙은_길_번호를_띄어_써도_붙여_읽는다() {
        assertThat(addressService.resolve("구로구 고척로 52마길 52-14").address().roadAddress())
                .isEqualTo("서울특별시 구로구 고척로52마길 52-14");
    }

    @Test
    void 같은_도로명이_여러_구에_있으면_구_이름으로_고르고_없으면_모호하다() {
        assertThat(addressService.resolve("서대문구 홍지문2길 6").address().managementNumber())
                .isEqualTo("1141011800100050052031388");
        assertThat(addressService.resolve("서울 종로구 홍지문2길 6").address().managementNumber())
                .isEqualTo("1111018500100600002025636");
        assertThat(addressService.resolve("홍지문2길 6").status()).isEqualTo(Status.AMBIGUOUS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"서울 영등포구 여의도 국제금융로 10", "여의도 국제금융로 10", "여의도역 국제금융로 10"})
    void 여의도처럼_도로_끝나는_서울_지명은_서울_밖으로_보지_않는다(final String freeText) {
        assertThat(addressService.resolve(freeText).address().managementNumber())
                .isEqualTo("1156011000100230000000001");
    }

    @Test
    void 입구로_끝나는_역_이름은_서울_밖_구로_보지_않는다() {
        assertThat(addressService.resolve("을지로입구 세종대로 110").address().managementNumber()).isEqualTo(CITY_HALL);
    }

    @Test
    void 쓴_구에_그_주소가_없으면_못_맞춘다() {
        assertThat(addressService.resolve("강남구 홍지문2길 6").status()).isEqualTo(Status.UNMATCHED);
    }

    @Test
    void 지하를_쓰면_지하_주소를_쓰지_않으면_지상_주소를_고른다() {
        final AddressResolution underground = addressService.resolve("종로구 세종대로 지하 175");

        assertThat(underground.address().managementNumber()).isEqualTo(SEJONG_175_UNDERGROUND);
        assertThat(underground.address().roadAddress()).isEqualTo("서울특별시 종로구 세종대로 지하 175");
        assertThat(addressService.resolve("종로구 세종대로 175").address().managementNumber()).isEqualTo(SEJONG_175);
    }

    @Test
    void 지번_하나에_건물이_여럿이면_모호하다() {
        assertThat(addressService.resolve("종로구 경운동 2").status()).isEqualTo(Status.AMBIGUOUS);
    }

    @Test
    void 산_지번과_일반_지번을_구분한다() {
        assertThat(addressService.resolve("종로구 옥인동 산3-1").address().managementNumber())
                .isEqualTo("1111011100200030001000001");
        assertThat(addressService.resolve("종로구 옥인동 3-1").address().managementNumber())
                .isEqualTo("1111011100100030001030495");
    }

    @Test
    void 가로_끝나는_법정동의_지번도_맞춘다() {
        assertThat(addressService.resolve("서울 종로1가 1").address().managementNumber())
                .isEqualTo("1111012600100010000000001");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "가상 주소", "세종대로 99999", "부산광역시 해운대구 세종대로 110", "경기 고양시 세종대로 110",
            "경기도 세종대로 110", "고양시 세종대로 110", "강원특별자치도 세종대로 110",
            "해운대구 세종대로 110", "분당구 세종대로 110", "수원 팔달구 세종대로 110"})
    void 기준_데이터에_없거나_서울_밖이면_못_맞춘다(final String freeText) {
        assertThat(addressService.resolve(freeText)).isEqualTo(new AddressResolution(Status.UNMATCHED, null));
    }

    static Stream<Arguments> aboveGroundRows() {
        return sample().stream().filter(row -> row[3].equals("0")).map(AddressServiceTest::argument);
    }

    static Stream<Arguments> uniqueJibunRows() {
        final Function<String[], String> jibun = row -> String.join(" ", row[7], row[8], row[9], row[10]);
        final Map<String, Long> counts = sample().stream().collect(Collectors.groupingBy(jibun, Collectors.counting()));
        return sample().stream().filter(row -> counts.get(jibun.apply(row)) == 1).map(AddressServiceTest::argument);
    }

    private static Arguments argument(final String[] row) {
        return Arguments.argumentSet(row[0], (Object) row);
    }

    private static List<String[]> sample() {
        try {
            final String csv = new ClassPathResource(SAMPLE).getContentAsString(StandardCharsets.UTF_8);
            return csv.lines().skip(1).map(line -> line.split(",", -1)).map(AddressServiceTest::nullIfEmpty).toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String[] nullIfEmpty(final String[] row) {
        return Arrays.stream(row).map(value -> value.isEmpty() ? null : value).toArray(String[]::new);
    }
}
