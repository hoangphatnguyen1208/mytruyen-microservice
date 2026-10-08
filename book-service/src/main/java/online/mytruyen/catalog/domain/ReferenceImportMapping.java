package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="reference_import_mappings") @Getter @Setter
public class ReferenceImportMapping extends AuditedEntity {
    @Id private UUID id;
    @Column(nullable=false,length=50) private String source;
    @Column(nullable=false,length=20) private String kind;
    @Column(name="external_id",nullable=false,length=150) private String externalId;
    @Column(name="author_id") private UUID authorId;
    @Column(name="genre_id") private Long genreId;
    @Column(name="tag_id") private Long tagId;
    @Column(name="status_id") private Long statusId;
    @Column(nullable=false,columnDefinition="text") private String payload;
    public String referenceId() {
        return switch(kind) {
            case "authors" -> authorId.toString();
            case "genres" -> genreId.toString();
            case "tags" -> tagId.toString();
            case "book-statuses" -> statusId.toString();
            default -> throw new IllegalStateException("Invalid reference kind");
        };
    }
}
