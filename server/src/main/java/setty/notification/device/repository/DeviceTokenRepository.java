package setty.notification.device.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.notification.device.domain.DeviceToken;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findAllByMemberIdIn(Collection<Long> memberIds);

    void deleteByToken(String token);

    void deleteByTokenIn(Collection<String> tokens);

    @Modifying
    @Query("delete from DeviceToken deviceToken where deviceToken.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
