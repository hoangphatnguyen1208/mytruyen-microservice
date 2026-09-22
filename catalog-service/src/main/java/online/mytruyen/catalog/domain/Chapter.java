package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "chapters")
@Getter @Setter
public class Chapter extends VersionedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false) private Book book;
    @Column(name = "creator_id", nullable = false) private UUID creatorId;
    @Column(name = "chapter_index", nullable = false) private int chapterIndex;
    @Column(nullable = false, length = 500) private String name;
    @Column(name = "word_count", nullable = false) private long wordCount;
    @Column(nullable = false) private boolean published;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
}
