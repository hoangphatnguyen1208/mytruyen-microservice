package online.mytruyen.catalog.repository;

import jakarta.persistence.LockModeType;
import online.mytruyen.catalog.domain.SearchOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface SearchOutboxRepository extends JpaRepository<SearchOutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SearchOutboxEvent e where e.publishedAt is null order by e.occurredAt, e.eventId")
    List<SearchOutboxEvent> lockPending(Pageable page);
}
