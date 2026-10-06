package online.mytruyen.identity.repository;

import jakarta.persistence.LockModeType;
import online.mytruyen.identity.domain.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    @Query("select t.session.user.id as userId, t.session.id as sessionId from IdentityRefreshToken t where t.tokenHash = :hash")
    Optional<Reference> reference(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from IdentityRefreshToken t where t.tokenHash = :hash")
    Optional<RefreshTokenEntity> lockByHash(@Param("hash") String hash);

    // Scalar projection avoids loading a stale token into the persistence context before waiting on the user lock.
    interface Reference {
        UUID getUserId();

        UUID getSessionId();
    }
}
