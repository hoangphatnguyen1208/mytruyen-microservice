package online.mytruyen.identity;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByIdAndDeletedAtIsNull(UUID id);
    Page<UserEntity> findByDeletedAtIsNull(Pageable pageable);
    @Query("select u.id from IdentityUser u where u.email = :email and u.deletedAt is null")
    Optional<UUID> findLoginId(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from IdentityUser u where u.id = :id")
    Optional<UserEntity> lockById(@Param("id") UUID id);
    @Query("select count(u) from IdentityUser u join u.roles r where r.id = 2 and u.active = true and u.deletedAt is null and (:excluded is null or u.id <> :excluded)")
    long countActiveAdmins(@Param("excluded") UUID excluded);
}
