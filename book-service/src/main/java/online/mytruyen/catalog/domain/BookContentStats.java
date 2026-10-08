package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "book_content_stats")
@Getter @Setter
public class BookContentStats extends AuditedEntity {
    @Id @Column(name = "book_id") private Long bookId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id") private Book book;
    @Column(name = "chapter_count", nullable = false) private long chapterCount;
    @Column(name = "word_count", nullable = false) private long wordCount;
    @Column(name = "latest_chapter_index") private Integer latestChapterIndex;
    @Column(name = "new_chap_at") private Instant newChapAt;
}
