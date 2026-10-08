package online.mytruyen.catalog.mapper;


import online.mytruyen.catalog.domain.*;
import static online.mytruyen.catalog.dto.CatalogDtos.*;

public final class Views {
    private Views() {}
    public static AuthorView author(Author a) {
        return a == null ? null : new AuthorView(a.getId(),a.getName(),a.getLocalName(),a.getAvatarUrl(),a.getCreatedAt(),a.getUpdatedAt());
    }
    public static TaxonView taxon(AuditedEntity value) {
        if (value instanceof Genre g) return new TaxonView(g.getId(),g.getName(),g.getSlug(),g.getDescription(),null,g.getCreatedAt(),g.getUpdatedAt());
        if (value instanceof Tag t) return new TaxonView(t.getId(),t.getName(),t.getSlug(),t.getDescription(),t.getType(),t.getCreatedAt(),t.getUpdatedAt());
        if (value instanceof BookStatus s) return new TaxonView(s.getId(),s.getName(),s.getSlug(),s.getDescription(),null,s.getCreatedAt(),s.getUpdatedAt());
        throw new IllegalArgumentException("Unknown taxonomy");
    }
    public static BookView book(Book b, BookContentStats counts, BookEngagementProjection engagement) {
        long reviews = engagement == null ? 0 : engagement.getReviewCount();
        return new BookView(b.getId(),b.getCreatorId(),b.getName(),b.getSlug(),b.getKind(),b.getSex(),b.getStatus().getId(),
            b.getChapterPerWeek(),b.isPublished(),b.getSynopsis(),b.getPoster(),b.getNote(),author(b.getAuthor()),taxon(b.getStatus()),
            b.getGenres().stream().map(Views::taxon).sorted(java.util.Comparator.comparing(TaxonView::id)).toList(),
            b.getTags().stream().map(Views::taxon).sorted(java.util.Comparator.comparing(TaxonView::id)).toList(),
            counts == null ? 0 : counts.getChapterCount(), counts == null ? 0 : counts.getWordCount(),
            counts == null ? null : counts.getLatestChapterIndex(), counts == null ? null : counts.getNewChapAt(),
            engagement == null ? 0 : engagement.getViewCount(),engagement == null ? 0 : engagement.getCommentCount(),
            reviews, reviews == 0 ? 0 : (double) engagement.getRatingSum()/reviews,
            engagement == null ? 0 : engagement.getBookmarkCount(),b.getCreatedAt(),b.getUpdatedAt(),b.getPublishedAt(),b.getVersion());
    }
}
