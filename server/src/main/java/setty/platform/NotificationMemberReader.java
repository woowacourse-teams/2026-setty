package setty.platform;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationMemberReader {

    private final JdbcTemplate jdbcTemplate;

    public NotificationMemberReader(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<MemberDeliveryArea> findDeliveryArea(final Long memberId) {
        return findDeliveryAreas(List.of(memberId)).stream().findFirst();
    }

    public List<MemberDeliveryArea> findDeliveryAreas(final Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        final String placeholders = String.join(",", Collections.nCopies(memberIds.size(), "?"));
        return jdbcTemplate.query(
                "SELECT id, dong_code, zip_code FROM members WHERE id IN (" + placeholders + ")",
                (resultSet, rowNum) -> new MemberDeliveryArea(
                        resultSet.getLong("id"), resultSet.getString("dong_code"), resultSet.getString("zip_code")),
                memberIds.toArray()
        );
    }
}
