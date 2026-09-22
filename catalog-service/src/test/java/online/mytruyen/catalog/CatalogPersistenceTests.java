package online.mytruyen.catalog;

import jakarta.persistence.*;
import online.mytruyen.catalog.domain.*;
import online.mytruyen.catalog.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.rabbitmq.listener.direct.auto-startup=false",
    "management.health.rabbit.enabled=false"
})
class CatalogPersistenceTests {
    @Autowired BookRepository books;
    @Autowired BookStatusRepository statuses;
    @Autowired AuthorRepository authors;
    @Autowired GenreRepository genres;
    @Autowired TagRepository tags;
    @Autowired ChapterRepository chapters;
    @Autowired ChapterContentRepository contents;
    @Autowired BookContentStatsRepository stats;
    @Autowired BookEngagementProjectionRepository engagement;
    @Autowired EntityManagerFactory entityManagers;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }
    Book book(String slug) {
        BookStatus status = new BookStatus();
        status.setSlug(UUID.randomUUID().toString());
        status.setName("Ongoing");
        statuses.save(status);
        Book b = new Book();
        b.setName("Truyện thử nghiệm");
        b.setSlug(slug);
        b.setStatus(status);
        b.setSynopsis("Synopsis");
        b.setCreatorId(UUID.randomUUID()); // Intentionally no Identity DB/FK.
        return books.saveAndFlush(b);
    }
    Chapter chapter(Book book, int index, boolean published) {
        Chapter c = new Chapter();
        c.setBook(book);
        c.setCreatorId(UUID.randomUUID());
        c.setChapterIndex(index);
        c.setName("Chapter " + index);
        c.setWordCount(100);
        c.setPublished(published);
        if (published) c.setPublishedAt(Instant.now());
        return chapters.saveAndFlush(c);
    }
    ChapterContent content(Chapter chapter) {
        ChapterContent cc = new ChapterContent();
        cc.setChapter(chapter);
        cc.setContent("Nội dung chương tiếng Việt");
        cc.setContentHash("a".repeat(64));
        return contents.saveAndFlush(cc);
    }

    @Test void flywayAppliedAndEntitiesValidateAgainstSchema() {
        assertThat(jdbc.queryForList("SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\"=TRUE AND \"type\"='SQL' ORDER BY \"installed_rank\"", String.class))
            .containsExactly("1", "2");
    }

    @Test void roundTripRelationsJsonAndSharedPrimaryKeys() {
        Long id = tx().execute(s -> {
            Book b = book(UUID.randomUUID().toString());
            Author author = new Author();
            author.setName("Same pen name");
            authors.saveAndFlush(author);
            b.setAuthor(author);
            Genre genre = new Genre();
            genre.setName("Fantasy");
            genre.setSlug(UUID.randomUUID().toString());
            b.getGenres().add(genres.save(genre));
            Tag tag = new Tag();
            tag.setName("Adventure");
            tag.setSlug(UUID.randomUUID().toString());
            tag.setType("theme");
            b.getTags().add(tags.save(tag));
            b.setPoster(Map.of("default", "https://example.com/poster.webp", "sizes", List.of(100, 200)));
            b.setPublished(true);
            b.setPublishedAt(Instant.now());
            books.flush();
            Chapter c = chapter(b, 1, true);
            assertThat(content(c).getChapterId()).isEqualTo(c.getId());
            BookContentStats counts = new BookContentStats();
            counts.setBook(b);
            assertThat(stats.saveAndFlush(counts).getBookId()).isEqualTo(b.getId());
            BookEngagementProjection projection = new BookEngagementProjection();
            projection.setBook(b);
            engagement.saveAndFlush(projection);
            return b.getId();
        });
        tx().executeWithoutResult(s -> {
            Book b = books.findById(id).orElseThrow();
            assertThat(b.getAuthor().getName()).isEqualTo("Same pen name");
            assertThat(b.getGenres()).hasSize(1);
            assertThat(b.getTags()).hasSize(1);
            assertThat(b.getPoster()).containsEntry("default", "https://example.com/poster.webp");
            assertThat(b.getCreatedAt()).isNotNull();
            assertThat(contents.findPublic(id, 1).orElseThrow().getContent()).contains("tiếng Việt");
        });
    }

    @Test void duplicateSlugAndChapterIndexAreRejected() {
        String slug = UUID.randomUUID().toString();
        Long id = tx().execute(s -> {
            Book b = book(slug);
            chapter(b, 1, false);
            return b.getId();
        });
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> book(slug)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> chapter(books.findById(id).orElseThrow(), 1, false)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void invalidIndexAndNegativeCountersAreRejected() {
        Long id = tx().execute(s -> book(UUID.randomUUID().toString()).getId());
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> chapter(books.findById(id).orElseThrow(), 0, false)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            BookContentStats value = new BookContentStats();
            value.setBook(books.findById(id).orElseThrow());
            value.setChapterCount(-1);
            stats.saveAndFlush(value);
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void publicReadsExcludeDraftDeletedAndHiddenParents() {
        Long id = tx().execute(s -> {
            Book b = book(UUID.randomUUID().toString());
            b.setPublished(true);
            Chapter live = chapter(b, 1, true);
            content(live);
            chapter(b, 2, false);
            Chapter deleted = chapter(b, 3, true);
            deleted.setDeletedAt(Instant.now());
            chapters.flush();
            return b.getId();
        });
        tx().executeWithoutResult(s -> {
            assertThat(chapters.findPublicByBookId(id, PageRequest.of(0, 10, Sort.by("chapterIndex"))).getTotalElements()).isEqualTo(1);
            assertThat(chapters.findPublic(id, 2)).isEmpty();
            assertThat(chapters.findPublic(id, 3)).isEmpty();
            Book b = books.findById(id).orElseThrow();
            b.setPublished(false);
            books.flush();
            assertThat(books.findPublicBySlug(b.getSlug())).isEmpty();
            assertThat(chapters.findPublic(id, 1)).isEmpty();
            assertThat(contents.findPublic(id, 1)).isEmpty();
            b.setPublished(true);
            b.setDeletedAt(Instant.now());
            books.flush();
            assertThat(books.findPublicBySlug(b.getSlug())).isEmpty();
            assertThat(chapters.findPublicByBookId(id, PageRequest.of(0, 10))).isEmpty();
            assertThat(contents.findPublic(id, 1)).isEmpty();
        });
    }

    @Test void summaryOnlyCountsPublishedNonDeletedChaptersAndHandlesEmptyBook() {
        tx().executeWithoutResult(s -> {
            Book b = book(UUID.randomUUID().toString());
            var empty = chapters.summarizePublished(b.getId());
            assertThat(empty.getChapterCount()).isZero();
            assertThat(empty.getWordCount()).isZero();
            assertThat(empty.getLatestChapterIndex()).isNull();
            chapter(b, 1, true);
            chapter(b, 5, true);
            chapter(b, 6, false);
            var deleted = chapter(b, 7, true);
            deleted.setDeletedAt(Instant.now());
            chapters.flush();
            var summary = chapters.summarizePublished(b.getId());
            assertThat(summary.getChapterCount()).isEqualTo(2);
            assertThat(summary.getWordCount()).isEqualTo(200);
            assertThat(summary.getLatestChapterIndex()).isEqualTo(5);
        });
    }

    @Test void physicalPurgeCascadesOwnedRowsButPreservesTaxonomy() {
        Long[] ids = tx().execute(s -> {
            Book b = book(UUID.randomUUID().toString());
            content(chapter(b, 1, false));
            Genre genre = new Genre();
            genre.setName("Genre");
            genre.setSlug(UUID.randomUUID().toString());
            b.getGenres().add(genres.save(genre));
            BookContentStats value = new BookContentStats();
            value.setBook(b);
            stats.saveAndFlush(value);
            return new Long[]{b.getId(), genre.getId(), b.getStatus().getId()};
        });
        assertThatThrownBy(() -> jdbc.update("DELETE FROM book_statuses WHERE id=?", ids[2]))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM genres WHERE id=?", ids[1]))
            .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM books WHERE id=?", ids[0]);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chapters WHERE book_id=?", Long.class, ids[0])).isZero();
        assertThat(stats.findById(ids[0])).isEmpty();
        assertThat(genres.existsById(ids[1])).isTrue();
        assertThat(statuses.existsById(ids[2])).isTrue();
    }

    @Test void staleBookUpdateIsRejectedByOptimisticVersion() {
        Long id = tx().execute(s -> book(UUID.randomUUID().toString()).getId());
        var first = entityManagers.createEntityManager();
        var second = entityManagers.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Book one = first.find(Book.class, id);
            Book two = second.find(Book.class, id);
            one.setName("Winner");
            first.getTransaction().commit();
            two.setName("Stale");
            assertThatThrownBy(() -> second.getTransaction().commit()).isInstanceOf(RollbackException.class);
            assertThat(books.findById(id).orElseThrow().getName()).isEqualTo("Winner");
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close();
            second.close();
        }
    }
}
