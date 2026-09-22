package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "book_engagement_projection")
@Getter @Setter
public class BookEngagementProjection extends AuditedEntity {
    @Id @Column(name = "book_id") private Long bookId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id") private Book book;
    @Column(name = "view_count", nullable = false) private long viewCount;
    @Column(name = "comment_count", nullable = false) private long commentCount;
    @Column(name = "review_count", nullable = false) private long reviewCount;
    @Column(name = "rating_sum", nullable = false) private long ratingSum;
    @Column(name = "bookmark_count", nullable = false) private long bookmarkCount;
    @Column(name = "source_version", nullable = false) private long sourceVersion;
}
