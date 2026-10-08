package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "chapter_contents")
@Getter @Setter
public class ChapterContent extends VersionedEntity {
    @Id @Column(name = "chapter_id") private Long chapterId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id") private Chapter chapter;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
}
