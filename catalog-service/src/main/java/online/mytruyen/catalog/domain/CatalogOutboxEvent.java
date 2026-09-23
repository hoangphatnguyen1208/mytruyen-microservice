package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="catalog_outbox")
@Getter @Setter
public class CatalogOutboxEvent {
    @Id private UUID eventId;
    @Column(nullable=false,length=50) private String aggregateType="Chapter";
    @Column(nullable=false) private Long aggregateId;
    @Column(nullable=false) private long aggregateVersion;
    @Column(nullable=false,length=100) private String eventType;
    @Column(nullable=false) private int schemaVersion=1;
    @Column(nullable=false) private UUID correlationId;
    @Column(nullable=false,columnDefinition="text") private String payload;
    @Column(nullable=false) private Instant occurredAt;
    private Instant publishedAt;
}
