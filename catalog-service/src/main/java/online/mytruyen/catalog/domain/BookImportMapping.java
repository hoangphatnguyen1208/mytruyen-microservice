package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="book_import_mappings") @Getter @Setter
public class BookImportMapping extends AuditedEntity {
    @Id private UUID id;
    @Column(nullable=false,length=50) private String source;
    @Column(name="external_id",nullable=false,length=150) private String externalId;
    @Column(name="book_id",nullable=false) private Long bookId;
    @Column(name="last_book_version",nullable=false) private long lastBookVersion;
    @Column(name="last_payload",nullable=false,columnDefinition="text") private String lastPayload;
}
