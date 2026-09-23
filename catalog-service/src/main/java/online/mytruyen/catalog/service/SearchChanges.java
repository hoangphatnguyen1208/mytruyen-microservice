package online.mytruyen.catalog.service;

import online.mytruyen.catalog.domain.SearchOutboxEvent;
import online.mytruyen.catalog.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SearchChanges {
    private final EntityManager entities;
    private final BookRepository books;

    public SearchChanges(EntityManager entities, BookRepository books) {
        this.entities = entities;
        this.books = books;
    }

    public void book(Long bookId) {
        enqueue(bookId);
    }

    private SearchOutboxEvent enqueue(Long bookId) {
        var event = new SearchOutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setBookId(bookId);
        event.setOccurredAt(Instant.now());
        entities.persist(event);
        return event;
    }

    public void author(UUID authorId) {
        // Keyset pagination avoids loading an author's complete catalogue in memory.
        // All jobs commit atomically with the rename; book reassignment also emits its own job.
        long after = 0;
        while (true) {
            var ids = books.findSearchIdsByAuthor(authorId, after, PageRequest.of(0, 100));
            if (ids.isEmpty()) return;
            var batch = new ArrayList<SearchOutboxEvent>();
            ids.forEach(id -> batch.add(enqueue(id)));
            entities.flush();
            batch.forEach(entities::detach);
            after = ids.get(ids.size() - 1);
        }
    }
}
