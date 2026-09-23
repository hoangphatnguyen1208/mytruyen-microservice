package online.mytruyen.catalog.repository;

import online.mytruyen.catalog.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {
    @Query("select count(b) from Book b where b.deletedAt is null and (:admin = true or b.published = true)")
    long countVisible(@Param("admin") boolean admin);
    @EntityGraph(attributePaths = {"author", "status"})
    List<Book> findByIdInAndPublishedTrueAndDeletedAtIsNull(Collection<Long> ids);
    @Override
    @EntityGraph(attributePaths = {"author", "status"})
    Page<Book> findAll(org.springframework.data.jpa.domain.Specification<Book> specification, Pageable pageable);
    Optional<Book> findByIdAndDeletedAtIsNull(Long id);
    Optional<Book> findBySlugAndDeletedAtIsNull(String slug);

    @EntityGraph(attributePaths = {"author", "status"})
    @Query("select b from Book b where b.slug = :slug and b.published = true and b.deletedAt is null")
    Optional<Book> findPublicBySlug(@Param("slug") String slug);

    @EntityGraph(attributePaths = {"author", "status"})
    @Query("select b from Book b where b.published = true and b.deletedAt is null")
    Page<Book> findPublic(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id and b.deletedAt is null")
    Optional<Book> lockById(@Param("id") Long id);
}
