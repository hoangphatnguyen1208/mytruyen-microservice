package online.mytruyen.identity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.*;

@Entity(name = "IdentityOutboxEvent")
@Table(name = "outbox_events")
@Getter @Setter @NoArgsConstructor
public class OutboxEventEntity {
    @Id @Column(name = "event_id") private UUID eventId;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "aggregate_version", nullable = false) private long aggregateVersion;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(name = "schema_version", nullable = false) private int schemaVersion = 1;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "published_at") private Instant publishedAt;
}
