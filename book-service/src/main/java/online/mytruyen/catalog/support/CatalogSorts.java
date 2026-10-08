package online.mytruyen.catalog.support;

import jakarta.persistence.criteria.*;
import online.mytruyen.catalog.domain.*;
import online.mytruyen.catalog.exception.ApiException;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.util.*;

public final class CatalogSorts {
    private CatalogSorts() {}
    private static final Map<String,String> BOOK_FIELDS=Map.ofEntries(
        Map.entry("id","id"),Map.entry("name","name"),Map.entry("slug","slug"),
        Map.entry("kind","kind"),Map.entry("sex","sex"),Map.entry("status_id","status.id"),
        Map.entry("chapter_per_week","chapterPerWeek"),Map.entry("published","published"),
        Map.entry("created_at","createdAt"),Map.entry("updated_at","updatedAt"),Map.entry("published_at","publishedAt"));
    private static final Map<String,String> CONTENT_FIELDS=Map.of(
        "chapter_count","chapterCount","word_count","wordCount","latest_chapter","latestChapterIndex","new_chap_at","newChapAt");
    private static final Map<String,String> ENGAGEMENT_FIELDS=Map.of(
        "view_count","viewCount","comment_count","commentCount","review_count","reviewCount","bookmark_count","bookmarkCount");
    private static final Map<String,String> CHAPTER_FIELDS=Map.ofEntries(
        Map.entry("id","id"),Map.entry("book_id","book.id"),Map.entry("index","chapterIndex"),
        Map.entry("name","name"),Map.entry("word_count","wordCount"),Map.entry("published","published"),
        Map.entry("created_at","createdAt"),Map.entry("updated_at","updatedAt"),Map.entry("published_at","publishedAt"));
    public record Key(String field,boolean descending) {}
    public static Key bookKey(String raw) {
        Key key=parse(raw,"-created_at");
        if (!BOOK_FIELDS.containsKey(key.field()) && !CONTENT_FIELDS.containsKey(key.field())
                && !ENGAGEMENT_FIELDS.containsKey(key.field()) && !key.field().equals("average_rating"))
            throw new ApiException(400,"Unsupported book sort");
        return key;
    }
    private static Key parse(String raw,String fallback) {
        String value=raw==null || raw.isEmpty() ? fallback : raw;
        return new Key(value.startsWith("-") ? value.substring(1) : value,value.startsWith("-"));
    }
    private static Path<?> path(Path<?> root,String property) {
        Path<?> value=root; for(String part:property.split("\\.")) value=value.get(part); return value;
    }
    public static Sort chapter(String raw,boolean allBooks) {
        if (raw==null || raw.isEmpty()) return allBooks
            ? Sort.by("book.id","chapterIndex","id") : Sort.by("chapterIndex","id");
        Key key=parse(raw,"index");
        String property=CHAPTER_FIELDS.get(key.field());
        if (property==null) throw new ApiException(400,"Unsupported chapter sort");
        Sort sort=Sort.by(key.descending() ? Sort.Direction.DESC : Sort.Direction.ASC,property);
        return property.equals("id") ? sort : sort.and(Sort.by("id"));
    }
    public static void orderBooks(Root<Book> root,CriteriaQuery<?> query,CriteriaBuilder cb,Key key) {
        // Count and page share visibility predicates, but only the page needs ordering subqueries.
        if (query.getResultType()==Long.class || query.getResultType()==long.class) return;
        Expression<?> value;
        if (BOOK_FIELDS.containsKey(key.field())) value=path(root,BOOK_FIELDS.get(key.field()));
        else if (key.field().equals("new_chap_at")) {
            var sub=query.subquery(Instant.class); var row=sub.from(BookContentStats.class);
            sub.select(row.get("newChapAt")).where(cb.equal(row.get("bookId"),root.get("id"))); value=sub;
        } else if (key.field().equals("latest_chapter")) {
            var sub=query.subquery(Integer.class); var row=sub.from(BookContentStats.class);
            sub.select(row.get("latestChapterIndex")).where(cb.equal(row.get("bookId"),root.get("id"))); value=sub;
        } else if (key.field().equals("average_rating")) {
            var sub=query.subquery(Double.class); var row=sub.from(BookEngagementProjection.class);
            var rating=cb.quot(row.get("ratingSum").as(Double.class),cb.nullif(row.<Long>get("reviewCount"),0L)).as(Double.class);
            sub.select(rating).where(cb.equal(row.get("bookId"),root.get("id")));
            value=cb.coalesce(sub,0d);
        } else {
            var sub=query.subquery(Long.class);
            Root<?> row=CONTENT_FIELDS.containsKey(key.field()) ? sub.from(BookContentStats.class) : sub.from(BookEngagementProjection.class);
            String property=CONTENT_FIELDS.getOrDefault(key.field(),ENGAGEMENT_FIELDS.get(key.field()));
            sub.select(row.get(property)).where(cb.equal(row.get("bookId"),root.get("id")));
            value=cb.coalesce(sub,0L);
        }
        query.orderBy(cb.asc(cb.<Integer>selectCase().when(cb.isNull(value),1).otherwise(0)),
            key.descending() ? cb.desc(value) : cb.asc(value),cb.asc(root.get("id")));
    }
}
