package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "tags")
@Getter @Setter
public class Tag extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 150) private String slug;
    @Column(nullable = false, length = 150) private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(nullable = false, length = 50) private String type;
}
