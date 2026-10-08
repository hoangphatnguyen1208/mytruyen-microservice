package online.mytruyen.catalog.service;

import online.mytruyen.catalog.domain.*;
import online.mytruyen.catalog.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

@Service
public class ChapterChanges {
    private final ChapterRepository chapters;
    private final BookContentStatsRepository stats;
    private final CatalogOutboxRepository events;
    private final ObjectMapper json;
    public ChapterChanges(ChapterRepository chapters, BookContentStatsRepository stats,
            CatalogOutboxRepository events, ObjectMapper json) {
        this.chapters=chapters; this.stats=stats; this.events=events; this.json=json;
    }
    // Caller holds the parent book lock. No broker/network work is performed here.
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(Chapter chapter,String type) {
        chapters.flush();
        var summary=chapters.summarizePublished(chapter.getBook().getId());
        var counters=stats.findById(chapter.getBook().getId()).orElseGet(()-> {
            var value=new BookContentStats(); value.setBook(chapter.getBook()); return value;
        });
        counters.setChapterCount(summary.getChapterCount());
        counters.setWordCount(summary.getWordCount());
        counters.setLatestChapterIndex(summary.getLatestChapterIndex());
        counters.setNewChapAt(summary.getNewChapAt());
        stats.saveAndFlush(counters);
        var event=new CatalogOutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setAggregateId(chapter.getId()); event.setAggregateVersion(chapter.getVersion());
        event.setEventType(type); event.setCorrelationId(UUID.randomUUID()); event.setOccurredAt(Instant.now());
        event.setPayload(json.writeValueAsString(Map.of(
            "book_id",chapter.getBook().getId(),"chapter_id",chapter.getId(),
            "index",chapter.getChapterIndex(),"published",chapter.isPublished(),
            "deleted",chapter.getDeletedAt()!=null)));
        events.saveAndFlush(event);
    }
}
