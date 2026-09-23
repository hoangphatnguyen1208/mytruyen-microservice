package online.mytruyen.catalog.repository;

import online.mytruyen.catalog.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface ChapterContentRepository extends JpaRepository<ChapterContent, Long> {
    @Query("select count(cc) from ChapterContent cc where cc.chapter.deletedAt is null and cc.chapter.book.deletedAt is null and (:admin = true or (cc.chapter.published = true and cc.chapter.book.published = true))")
    long countVisible(@Param("admin") boolean admin);
    @Query("select cc from ChapterContent cc where cc.chapter.book.id = :bookId and cc.chapter.chapterIndex = :index and cc.chapter.published = true and cc.chapter.deletedAt is null and cc.chapter.book.published = true and cc.chapter.book.deletedAt is null")
    Optional<ChapterContent> findPublic(@Param("bookId") Long bookId, @Param("index") int index);
}
