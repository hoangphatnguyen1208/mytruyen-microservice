package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="legacy_worker_books") @Getter @Setter
public class LegacyWorkerBook extends AuditedEntity {
    @Id @Column(name="external_id") private Long externalId;
    @Column(name="book_id",nullable=false) private Long bookId;
    @Column(nullable=false,columnDefinition="text") private String snapshot;
}
