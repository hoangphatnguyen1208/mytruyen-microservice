package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.BatchSize;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Entity @Table(name = "books")
@Getter @Setter
public class Book extends VersionedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id") private Author author;
    @Column(name = "creator_id", nullable = false) private UUID creatorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false) private BookStatus status;
    @Column(nullable = false, length = 500) private String name;
    @Column(nullable = false, length = 500) private String slug;
    @Column(nullable = false) private int kind;
    @Column(nullable = false) private int sex;
    @Column(nullable = false, columnDefinition = "text") private String synopsis;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Map<String, Object> poster;
    @Column(columnDefinition = "text") private String note;
    @Column(name = "chapter_per_week", nullable = false) private int chapterPerWeek;
    @Column(nullable = false) private boolean published;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "book_genres", joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "genre_id"))
    @BatchSize(size = 100)
    private Set<Genre> genres = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "book_tags", joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @BatchSize(size = 100)
    private Set<Tag> tags = new LinkedHashSet<>();
}
