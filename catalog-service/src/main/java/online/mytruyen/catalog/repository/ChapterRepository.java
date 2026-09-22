package online.mytruyen.catalog.repository;

import online.mytruyen.catalog.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    Optional<Chapter> findByBookIdAndChapterIndexAndDeletedAtIsNull(Long bookId, int chapterIndex);

    @Query("select c from Chapter c where c.book.id = :bookId and c.published = true and c.deletedAt is null and c.book.published = true and c.book.deletedAt is null")
    Page<Chapter> findPublicByBookId(@Param("bookId") Long bookId, Pageable pageable);

    @Query("select c from Chapter c where c.book.id = :bookId and c.chapterIndex = :index and c.published = true and c.deletedAt is null and c.book.published = true and c.book.deletedAt is null")
    Optional<Chapter> findPublic(@Param("bookId") Long bookId, @Param("index") int index);

    interface PublishedSummary {
        Long getChapterCount();
        Long getWordCount();
        Integer getLatestChapterIndex();
        Instant getNewChapAt();
    }

    @Query("select count(c) as chapterCount, coalesce(sum(c.wordCount), 0) as wordCount, max(c.chapterIndex) as latestChapterIndex, max(c.publishedAt) as newChapAt from Chapter c where c.book.id = :bookId and c.published = true and c.deletedAt is null")
    PublishedSummary summarizePublished(@Param("bookId") Long bookId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Chapter c where c.id = :id and c.deletedAt is null")
    Optional<Chapter> lockById(@Param("id") Long id);
}
