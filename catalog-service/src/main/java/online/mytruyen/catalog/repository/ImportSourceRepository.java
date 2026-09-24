package online.mytruyen.catalog.repository;

import online.mytruyen.catalog.domain.ImportSource;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ImportSourceRepository extends JpaRepository<ImportSource,String> {
    // A registered source is a stable lock even when an external ID is not mapped yet.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ImportSource s where s.code=:code")
    Optional<ImportSource> lockByCode(@Param("code") String code);
}
