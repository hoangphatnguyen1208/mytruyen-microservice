package online.mytruyen.catalog.service;

import online.mytruyen.catalog.exception.ApiException;
import online.mytruyen.catalog.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StatisticsService {
    private final BookRepository books;
    private final ChapterRepository chapters;
    private final ChapterContentRepository contents;

    public StatisticsService(BookRepository books, ChapterRepository chapters, ChapterContentRepository contents) {
        this.books = books;
        this.chapters = chapters;
        this.contents = contents;
    }

    public long count(String resource, boolean admin) {
        return switch (resource) {
            case "books" -> books.countVisible(admin);
            case "chapters" -> chapters.countVisible(admin);
            case "chapter_content" -> contents.countVisible(admin);
            default -> throw ApiException.missing("Statistic not found");
        };
    }
}
