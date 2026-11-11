package setty.notification.device.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.notification.device.domain.DeviceToken;
import setty.notification.owner.OwnerType;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findAllByOwnerTypeAndOwnerIdIn(OwnerType ownerType, Collection<Long> ownerIds);

    void deleteByToken(String token);

    void deleteByTokenIn(Collection<String> tokens);

    @Modifying
    @Query("delete from DeviceToken deviceToken "
            + "where deviceToken.ownerType = :ownerType and deviceToken.ownerId = :ownerId")
    void deleteAllByOwner(@Param("ownerType") OwnerType ownerType, @Param("ownerId") Long ownerId);
}
