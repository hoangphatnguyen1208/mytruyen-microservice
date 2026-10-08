package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "genres")
@Getter @Setter
public class Genre extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 150) private String slug;
    @Column(nullable = false, length = 150) private String name;
    @Column(columnDefinition = "text") private String description;
}
