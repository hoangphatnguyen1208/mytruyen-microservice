package online.mytruyen.identity.repository;

import online.mytruyen.identity.domain.RefreshTokenEntity;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    // Scalar projection avoids loading a stale token into the persistence context before waiting on the user lock.
    interface Reference {
        UUID getUserId();
        UUID getSessionId();
    }
    @Query("select t.session.user.id as userId, t.session.id as sessionId from IdentityRefreshToken t where t.tokenHash = :hash")
    Optional<Reference> reference(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from IdentityRefreshToken t where t.tokenHash = :hash")
    Optional<RefreshTokenEntity> lockByHash(@Param("hash") String hash);
}
