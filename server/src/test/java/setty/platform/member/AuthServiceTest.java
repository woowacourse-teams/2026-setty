package setty.platform.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import setty.address.AddressResolution.Status;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.member.controller.dto.MemberMeResponse;
import setty.platform.member.controller.dto.SignupRequest;
import setty.platform.member.controller.dto.UpdateDongRequest;
import setty.platform.member.controller.dto.UpdateProfileRequest;
import setty.platform.member.domain.Member;
import setty.platform.member.domain.ResolvedAddress;
import setty.platform.member.repository.MemberRepository;
import setty.platform.member.service.AuthService;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class AuthServiceTest extends MySqlIntegrationTestSupport {

    private static final long MEMBER_ID = 101L;
    private static final String TOKEN = "token-101";
    private static final String CITY_HALL = "1114010300100310000019224";
    private static final String CITY_HALL_DONG = "1114055000";

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(MEMBER_ID, TOKEN);
        jdbcTemplate.update("""
                INSERT INTO address_reference (mgmt_no, road_code, road_name, underground, building_main, building_sub,
                    sigungu, legal_dong, jibun_mountain, jibun_main, jibun_sub, zip_code, dong_code)
                VALUES (?, '111402005001', '세종대로', 0, 110, 0, '중구', '태평로1가', 0, 31, 0, '04524', ?)
                """, CITY_HALL, CITY_HALL_DONG);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM members");
        jdbcTemplate.update("DELETE FROM address_reference");
    }

    @Test
    void 로그아웃하면_토큰이_무효화된다() {
        authService.logout(MEMBER_ID);

        assertThat(memberRepository.findByToken(TOKEN)).isEmpty();
    }

    @Test
    void 존재하지_않는_회원_로그아웃은_거부된다() {
        assertThatThrownBy(() -> authService.logout(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 연락처와_주소를_수정한다() {
        final MemberMeResponse response = authService.updateProfile(
                MEMBER_ID, new UpdateProfileRequest("010-1111-2222", "서울시 새주소구 변경로 1"));

        assertThat(response.phoneNumber()).isEqualTo("010-1111-2222");
        assertThat(response.address()).isEqualTo("서울시 새주소구 변경로 1");

        final Member reloaded = memberRepository.findById(MEMBER_ID).orElseThrow();
        assertThat(reloaded.getPhoneNumber()).isEqualTo("010-1111-2222");
        assertThat(reloaded.getAddress()).isEqualTo("서울시 새주소구 변경로 1");
    }

    @Test
    void 존재하지_않는_회원_정보수정은_거부된다() {
        assertThatThrownBy(() -> authService.updateProfile(
                999L, new UpdateProfileRequest("010-1111-2222", "서울시 새주소구 변경로 1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 가입할_때_고른_행정동을_저장한다() {
        final Member member = authService.signup(
                new SignupRequest("newmember", "password1", "010-1111-2222", "가상 주소", "1111053000"));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getDongCode()).isEqualTo("1111053000");
    }

    @Test
    void 행정동_없이도_가입할_수_있다() {
        final Member member = authService.signup(
                new SignupRequest("newmember", "password1", "010-1111-2222", "가상 주소", null));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getDongCode()).isNull();
    }

    @Test
    void 서울_행정동이_아니면_가입을_거부한다() {
        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("newmember", "password1", "010-1111-2222", "가상 주소", "4113510300")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DELIVERY_AREA_NOT_SUPPORTED);
    }

    @Test
    void 행정동을_바꾼다() {
        final MemberMeResponse response = authService.updateDong(MEMBER_ID, new UpdateDongRequest("1165052000"));

        assertThat(response.dongCode()).isEqualTo("1165052000");
        assertThat(memberRepository.findById(MEMBER_ID).orElseThrow().getDongCode()).isEqualTo("1165052000");
    }

    @Test
    void 서울_행정동이_아니면_행정동_변경을_거부한다() {
        assertThatThrownBy(() -> authService.updateDong(MEMBER_ID, new UpdateDongRequest("4113510300")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DELIVERY_AREA_NOT_SUPPORTED);

        assertThat(memberRepository.findById(MEMBER_ID).orElseThrow().getDongCode()).isNull();
    }

    @Test
    void 가입할_때_주소를_맞추면_표준_주소를_저장하고_빈_행정동을_채운다() {
        final Member member = authService.signup(new SignupRequest(
                "newmember", "password1", "010-1111-2222", "서울시 중구 세종대로 110, 3층", null));

        final Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        final ResolvedAddress resolved = reloaded.getResolvedAddress();
        assertThat(resolved.getStatus()).isEqualTo(Status.MATCHED);
        assertThat(resolved.getManagementNumber()).isEqualTo(CITY_HALL);
        assertThat(resolved.getRoadAddress()).isEqualTo("서울특별시 중구 세종대로 110");
        assertThat(resolved.getDetail()).isEqualTo("3층");
        assertThat(resolved.getZipCode()).isEqualTo("04524");
        assertThat(reloaded.getAddress()).isEqualTo("서울시 중구 세종대로 110, 3층");
        assertThat(reloaded.getDongCode()).isEqualTo(CITY_HALL_DONG);
    }

    @Test
    void 가입할_때_고른_행정동은_주소를_맞춰도_바꾸지_않는다() {
        final Member member = authService.signup(new SignupRequest(
                "newmember", "password1", "010-1111-2222", "서울시 중구 세종대로 110", "1111053000"));

        final Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getResolvedAddress().getStatus()).isEqualTo(Status.MATCHED);
        assertThat(reloaded.getDongCode()).isEqualTo("1111053000");
    }

    @Test
    void 가입할_때_주소를_못_맞춰도_가입하고_상태만_남긴다() {
        final Member member = authService.signup(
                new SignupRequest("newmember", "password1", "010-1111-2222", "가상 주소", null));

        final Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getResolvedAddress().getStatus()).isEqualTo(Status.UNMATCHED);
        assertThat(reloaded.getResolvedAddress().getManagementNumber()).isNull();
        assertThat(reloaded.getDongCode()).isNull();
    }

    @Test
    void 주소를_수정할_때_맞추면_표준_주소를_저장하고_빈_행정동을_채운다() {
        final MemberMeResponse response = authService.updateProfile(
                MEMBER_ID, new UpdateProfileRequest("010-1111-2222", "서울 중구 세종대로 110"));

        assertThat(response.address()).isEqualTo("서울 중구 세종대로 110");
        assertThat(response.dongCode()).isEqualTo(CITY_HALL_DONG);
        final Member reloaded = memberRepository.findById(MEMBER_ID).orElseThrow();
        assertThat(reloaded.getResolvedAddress().getStatus()).isEqualTo(Status.MATCHED);
        assertThat(reloaded.getResolvedAddress().getManagementNumber()).isEqualTo(CITY_HALL);
    }

    @Test
    void 주소를_수정할_때_이미_고른_행정동은_바꾸지_않는다() {
        jdbcTemplate.update("UPDATE members SET dong_code = '1165052000' WHERE id = ?", MEMBER_ID);

        authService.updateProfile(MEMBER_ID, new UpdateProfileRequest("010-1111-2222", "서울 중구 세종대로 110"));

        final Member reloaded = memberRepository.findById(MEMBER_ID).orElseThrow();
        assertThat(reloaded.getResolvedAddress().getStatus()).isEqualTo(Status.MATCHED);
        assertThat(reloaded.getDongCode()).isEqualTo("1165052000");
    }

    @Test
    void 못_맞추는_주소로_수정하면_이전_표준_주소를_지운다() {
        authService.updateProfile(MEMBER_ID, new UpdateProfileRequest("010-1111-2222", "서울 중구 세종대로 110"));

        authService.updateProfile(MEMBER_ID, new UpdateProfileRequest("010-1111-2222", "가상 주소"));

        final ResolvedAddress resolved = memberRepository.findById(MEMBER_ID).orElseThrow().getResolvedAddress();
        assertThat(resolved.getStatus()).isEqualTo(Status.UNMATCHED);
        assertThat(resolved.getManagementNumber()).isNull();
        assertThat(resolved.getRoadAddress()).isNull();
    }

    private void insertMember(final long memberId, final String token) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """,
                memberId,
                "member" + memberId,
                token
        );
    }
}
