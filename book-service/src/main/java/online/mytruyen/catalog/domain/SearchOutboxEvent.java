package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_outbox")
@Getter @Setter
public class SearchOutboxEvent {
    @Id private UUID eventId;
    @Column(nullable = false) private Long bookId;
    @Column(nullable = false) private Instant occurredAt;
    private Instant publishedAt;
}
