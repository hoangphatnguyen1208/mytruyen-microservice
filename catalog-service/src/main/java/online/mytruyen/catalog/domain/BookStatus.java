package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "book_statuses")
@Getter @Setter
public class BookStatus extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String slug;
    @Column(nullable = false, length = 100) private String name;
    @Column(columnDefinition = "text") private String description;
}
