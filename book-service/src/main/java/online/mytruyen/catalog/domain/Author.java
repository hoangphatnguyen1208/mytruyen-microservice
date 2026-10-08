package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "authors")
@Getter @Setter
public class Author extends VersionedEntity {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false, length = 255) private String name;
    @Column(name = "local_name", length = 255) private String localName;
    @Column(name = "avatar_url", columnDefinition = "text") private String avatarUrl;
}
