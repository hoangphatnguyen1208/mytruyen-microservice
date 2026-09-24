package online.mytruyen.catalog.service;

import jakarta.validation.Validator;
import online.mytruyen.catalog.domain.*;
import online.mytruyen.catalog.dto.CatalogDtos.*;
import online.mytruyen.catalog.dto.LegacyWorkerDtos.*;
import online.mytruyen.catalog.exception.ApiException;
import online.mytruyen.catalog.repository.*;
import online.mytruyen.catalog.mapper.ChapterViews;
import online.mytruyen.catalog.mapper.Views;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class LegacyWorkerService {
    private static final Set<String> COUNTERS=Set.of("chapter_count","word_count","view_count","comment_count","review_count","average_rating","bookmark_count");
    private static final Set<String> FIELDS=Set.of("id","name","slug","author_id","status_id","kind","sex","synopsis","poster","note","chapter_per_week","published","genre_ids","tag_ids",
        "chapter_count","word_count","view_count","comment_count","review_count","average_rating","bookmark_count");
    private final LegacyWorkerBookRepository links;
    private final ImportSourceRepository sources;
    private final BookRepository books;
    private final BookStatusRepository statuses;
    private final BookService bookService;
    private final TaxonomyService taxonomy;
    private final AuthorRepository authors;
    private final GenreRepository genres;
    private final TagRepository tags;
    private final ChapterRepository chapters;
    private final ChapterChanges changes;
    private final ObjectMapper json;
    private final Validator validator;
    private final Map<Long,String> statusMap=new HashMap<>();

    public LegacyWorkerService(LegacyWorkerBookRepository links,ImportSourceRepository sources,BookRepository books,
            BookStatusRepository statuses,BookService bookService,TaxonomyService taxonomy,ChapterRepository chapters,
            ChapterChanges changes,ObjectMapper json,Validator validator,AuthorRepository authors,GenreRepository genres,TagRepository tags,
            @Value("${catalog.legacy-worker.status-map:{}}") String configuredStatuses) {
        this.links=links; this.sources=sources; this.books=books; this.statuses=statuses; this.bookService=bookService;
        this.taxonomy=taxonomy; this.chapters=chapters; this.changes=changes; this.json=json; this.validator=validator;
        this.authors=authors; this.genres=genres; this.tags=tags;
        try {
            var node=json.readTree(configuredStatuses.isBlank() ? "{}" : configuredStatuses);
            if (!node.isObject()) throw new IllegalArgumentException();
            node.properties().forEach(entry->{
                long id=Long.parseLong(entry.getKey()); String slug=entry.getValue().asString();
                if (id<=0 || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length()>100) throw new IllegalArgumentException();
                statusMap.put(id,slug);
            });
        } catch (Exception e) { throw new IllegalArgumentException("LEGACY_WORKER_STATUS_MAP must map positive source IDs to status slugs"); }
    }
    private void lockSource() {
        var source=sources.lockByCode("metruyencv").orElseThrow(ApiException::missing);
        if (!source.isEnabled()) throw new ApiException(409,"Import source disabled");
    }
    private LegacyWorkerBook link(long externalId) {
        if (externalId<1) throw new ApiException(400,"Positive book ID required");
        return links.findById(externalId).orElseThrow(()->ApiException.missing("Legacy book mapping not found"));
    }
    private Book lockBook(LegacyWorkerBook link) {
        return books.lockById(link.getBookId()).orElseThrow(()->new ApiException(409,"Mapped book deleted; manual review required"));
    }
    private long status(long sourceId) {
        String slug=statusMap.get(sourceId);
        if (slug==null) throw new ApiException(409,"Source status ID is not configured");
        return statuses.findBySlug(slug).orElseThrow(()->new ApiException(409,"Configured status must be created first")).getId();
    }
    private Map<String,Object> map(Object value) { return json.convertValue(value,new TypeReference<LinkedHashMap<String,Object>>() {}); }
    private Map<String,Object> snapshot(LegacyWorkerBook link) {
        return json.readValue(link.getSnapshot(),new TypeReference<LinkedHashMap<String,Object>>() {});
    }
    private BookInput input(Map<String,Object> values) {
        if (!FIELDS.containsAll(values.keySet())) throw new ApiException(400,"Unknown worker book field");
        var normalized=new LinkedHashMap<>(values);
        for (String key:COUNTERS) normalized.putIfAbsent(key,0);
        normalized.putIfAbsent("genre_ids",List.of()); normalized.putIfAbsent("tag_ids",List.of());
        normalized.putIfAbsent("chapter_per_week",0);
        normalized.putIfAbsent("synopsis","");
        if (normalized.get("note") instanceof List<?> note && note.isEmpty()) normalized.put("note","");
        if (normalized.get("poster") instanceof String poster)
            normalized.put("poster",poster.isBlank() ? Map.of() : Map.of("default",poster));
        try {
            var input=json.convertValue(normalized,BookInput.class);
            if (!validator.validate(input).isEmpty() || !Double.isFinite(input.average_rating())) throw new IllegalArgumentException();
            return input;
        } catch (Exception e) { throw new ApiException(400,"Invalid worker book payload"); }
    }
    private Map<String,Object> view(LegacyWorkerBook link) {
        var view=map(bookService.detail(link.getBookId(),true));
        var snapshot=snapshot(link);
        view.put("id",link.getExternalId()); view.put("status_id",snapshot.get("status_id"));
        for (String key:COUNTERS) view.put(key,snapshot.get(key));
        return view;
    }
    public Map<String,Object> detail(long externalId) {
        lockSource(); var link=link(externalId); lockBook(link); return view(link);
    }
    public Map<String,Object> create(Map<String,Object> payload,UUID creator) {
        var input=input(payload); lockSource();
        var existing=links.findById(input.id());
        if (existing.isPresent()) {
            var link=existing.get(); lockBook(link);
            if (!json.readTree(json.writeValueAsString(input)).equals(json.readTree(link.getSnapshot())))
                throw new ApiException(409,"Source ID already linked; use update");
            return view(link);
        }
        var created=bookService.create(input.asBook(status(input.status_id())),creator);
        var link=new LegacyWorkerBook(); link.setExternalId(input.id()); link.setBookId(created.id());
        link.setSnapshot(json.writeValueAsString(input)); links.saveAndFlush(link); return view(link);
    }
    public Map<String,Object> update(long externalId,Map<String,Object> patch) {
        if (patch.containsKey("id")) throw new ApiException(400,"Cannot change source ID");
        lockSource(); var link=link(externalId); lockBook(link);
        // Start from the current aggregate to preserve fields the original PATCH did not send.
        var current=bookService.detail(link.getBookId(),true);
        var base=map(new BookWrite(current.name(),current.slug(),current.author()==null ? null : current.author().id(),
            current.status_id(),current.kind(),current.sex(),current.synopsis(),current.poster(),current.note(),current.chapter_per_week(),
            current.published(),current.genres().stream().map(TaxonView::id).toList(),current.tags().stream().map(TaxonView::id).toList()));
        var previous=snapshot(link); base.put("id",externalId); base.put("status_id",previous.get("status_id"));
        for (String key:COUNTERS) base.put(key,previous.get(key));
        base.putAll(patch); var input=input(base);
        // Unlike the experimental importer, this adapter intentionally retains legacy overwrite semantics.
        var updated=input.asBook(status(input.status_id()));
        // BookService's modern PATCH requires nonblank synopsis; use the dedicated legacy replacement instead.
        bookService.replaceForLegacyWorker(link.getBookId(),updated);
        link.setSnapshot(json.writeValueAsString(input)); links.saveAndFlush(link); return view(link);
    }
    public Map<String,Object> bind(long externalId,BindInput input) {
        lockSource(); if (externalId<1) throw new ApiException(400,"Positive source ID required");
        if (links.existsById(externalId)) throw new ApiException(409,"Source ID already linked");
        var book=books.lockById(input.book_id()).orElseThrow(ApiException::missing);
        if (book.getStatus().getId()!=status(input.source_status_id())) throw new ApiException(409,"Source status mapping does not match existing book");
        var current=bookService.detail(book.getId(),true);
        var payload=map(new BookInput(externalId,current.name(),current.slug(),current.author()==null ? null : current.author().id(),
            input.source_status_id(),current.kind(),current.sex(),current.synopsis(),current.poster(),current.note(),current.chapter_per_week(),
            current.published(),current.genres().stream().map(TaxonView::id).toList(),current.tags().stream().map(TaxonView::id).toList(),
            current.chapter_count(),current.word_count(),current.view_count(),current.comment_count(),current.review_count(),current.average_rating(),current.bookmark_count()));
        var link=new LegacyWorkerBook(); link.setExternalId(externalId); link.setBookId(book.getId());
        link.setSnapshot(json.writeValueAsString(input(payload))); links.saveAndFlush(link); return view(link);
    }
    public AuthorView author(String name) { lockSource(); return taxonomy.authorByName(name); }
    public AuthorView createAuthor(AuthorWrite input) {
        lockSource();
        var found=authors.findByName(input.name());
        if (found.size()>1) throw new ApiException(409,"Multiple authors share this name");
        if (!found.isEmpty()) return Views.author(found.get(0));
        return taxonomy.createAuthor(input);
    }
    public TaxonView taxon(String kind,String slug) { lockSource(); return taxonomy.get(kind,slug); }
    public TaxonView createTaxon(String kind,TaxonWrite input) {
        lockSource();
        Optional<TaxonView> found=switch(kind) {
            case "genres" -> genres.findBySlug(input.slug()).map(Views::taxon);
            case "tags" -> tags.findBySlug(input.slug()).map(Views::taxon);
            case "book-statuses" -> statuses.findBySlug(input.slug()).map(Views::taxon);
            default -> throw ApiException.missing();
        };
        if (found.isPresent()) return found.get();
        return taxonomy.create(kind,input);
    }
    private Map<String,Object> chapterView(long externalId,Chapter chapter) {
        var result=map(ChapterViews.chapter(chapter)); result.put("book_id",externalId); return result;
    }
    public Map<String,Object> chapter(long externalId,int index) {
        lockSource(); var link=link(externalId); lockBook(link);
        var chapter=chapters.findByBookIdAndChapterIndexAndDeletedAtIsNull(link.getBookId(),index).orElseThrow(ApiException::missing);
        return chapterView(externalId,chapter);
    }
    public Map<String,Object> createChapter(long externalId,ChapterInput input,UUID creator) {
        lockSource(); var link=link(externalId); var book=lockBook(link);
        var found=chapters.findByBookIdAndChapterIndexAndDeletedAtIsNull(book.getId(),input.index());
        if (found.isPresent()) {
            var c=found.get();
            if (!c.getName().equals(input.name()) || c.getWordCount()!=input.word_count() || c.isPublished()!=input.published())
                throw new ApiException(409,"Chapter already exists with different metadata");
            return chapterView(externalId,c);
        }
        var c=new Chapter(); c.setBook(book); c.setCreatorId(creator); c.setChapterIndex(input.index());
        c.setName(input.name()); c.setWordCount(input.word_count()); c.setPublished(input.published());
        if (input.published()) c.setPublishedAt(Instant.now());
        chapters.saveAndFlush(c); changes.record(c,"LegacyChapterImported");
        return chapterView(externalId,c);
    }
}
