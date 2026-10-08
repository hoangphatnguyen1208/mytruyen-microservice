package online.mytruyen.catalog.repository;

import online.mytruyen.catalog.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface AuthorRepository extends JpaRepository<Author, UUID> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select a from Author a where a.id = :id")
    Optional<Author> lockForBookAssignment(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Author a where a.id = :id")
    Optional<Author> lockForRename(@Param("id") UUID id);
    List<Author> findByName(String name);
}
