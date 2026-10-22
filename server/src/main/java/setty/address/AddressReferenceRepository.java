package setty.address;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AddressReferenceRepository {

    private static final String SELECT = """
            SELECT mgmt_no, sigungu, road_name, underground, building_main, building_sub, jibun_mountain,
                   zip_code, dong_code, latitude, longitude
            FROM address_reference
            """;

    private final JdbcTemplate jdbcTemplate;

    List<Row> findByRoad(final String roadName, final int main, final int sub) {
        return jdbcTemplate.query(SELECT + "WHERE road_name = ? AND building_main = ? AND building_sub = ?",
                AddressReferenceRepository::row, roadName, main, sub);
    }

    List<Row> findByJibun(final String legalDong, final int main, final int sub) {
        return jdbcTemplate.query(SELECT + "WHERE legal_dong = ? AND jibun_main = ? AND jibun_sub = ?",
                AddressReferenceRepository::row, legalDong, main, sub);
    }

    private static Row row(final ResultSet rs, final int rowNum) throws SQLException {
        return new Row(
                rs.getString("mgmt_no"),
                rs.getString("sigungu"),
                rs.getString("road_name"),
                rs.getBoolean("underground"),
                rs.getInt("building_main"),
                rs.getInt("building_sub"),
                rs.getBoolean("jibun_mountain"),
                rs.getString("zip_code"),
                rs.getString("dong_code"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude")
        );
    }

    record Row(
            String managementNumber,
            String district,
            String roadName,
            boolean underground,
            int buildingMain,
            int buildingSub,
            boolean mountain,
            String zipCode,
            String dongCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {

        StandardAddress toStandard(final String detail) {
            final String number = buildingSub == 0 ? String.valueOf(buildingMain) : buildingMain + "-" + buildingSub;
            final String roadAddress = "서울특별시 " + district + " " + roadName + " " + (underground ? "지하 " : "") + number;
            return new StandardAddress(managementNumber, roadAddress, detail, zipCode, dongCode, latitude, longitude);
        }
    }
}
