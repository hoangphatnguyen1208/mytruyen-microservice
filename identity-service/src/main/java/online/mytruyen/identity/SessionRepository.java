package online.mytruyen.identity;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IdentitySession s where s.id = :id")
    Optional<SessionEntity> lockById(@Param("id") UUID id);
    @Modifying(flushAutomatically = true)
    @Query("update IdentitySession s set s.revokedAt = :now where s.user.id = :userId and s.revokedAt is null")
    int revokeAll(@Param("userId") UUID userId, @Param("now") Instant now);
    @Query("select count(s) > 0 from IdentitySession s where s.id = :sid and s.user.id = :uid and s.user.active = true and s.user.deletedAt is null and s.revokedAt is null and s.expiresAt > :now")
    boolean active(@Param("uid") UUID uid, @Param("sid") UUID sid, @Param("now") Instant now);
}
