package online.mytruyen.catalog.service;

import online.mytruyen.catalog.dto.ApiResponses;
import online.mytruyen.catalog.exception.ApiException;
import online.mytruyen.catalog.mapper.Views;
import online.mytruyen.catalog.support.Patches;
import online.mytruyen.catalog.support.CatalogSorts;

import online.mytruyen.catalog.dto.CatalogDtos.*;
import online.mytruyen.catalog.domain.*;
import online.mytruyen.catalog.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BookService {
    private final BookRepository books;
    private final AuthorRepository authors;
    private final BookStatusRepository statuses;
    private final GenreRepository genres;
    private final TagRepository tags;
    private final BookContentStatsRepository stats;
    private final BookEngagementProjectionRepository engagement;
    private final Patches patches;
    public BookService(BookRepository books, AuthorRepository authors, BookStatusRepository statuses,
            GenreRepository genres, TagRepository tags, BookContentStatsRepository stats,
            BookEngagementProjectionRepository engagement, Patches patches) {
        this.books=books; this.authors=authors; this.statuses=statuses; this.genres=genres;
        this.tags=tags; this.stats=stats; this.engagement=engagement; this.patches=patches;
    }
    private Book get(Long id) {
        return books.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.missing("Book not found"));
    }
    private BookView view(Book book) {
        return Views.book(book, stats.findById(book.getId()).orElse(null),
            engagement.findById(book.getId()).orElse(null));
    }
    public BookView detail(Long id, boolean admin) {
        Book book=get(id);
        if (!admin && !book.isPublished()) throw ApiException.missing("Book not found");
        return view(book);
    }
    public BookView bySlug(String slug) {
        return view(books.findPublicBySlug(slug).orElseThrow(() -> ApiException.missing("Book not found")));
    }
    public List<BookView> batch(List<Long> ids) {
        if (ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(id->id==null || id<1))
            throw new ApiException(400,"Supply between 1 and 100 positive book IDs");
        var ordered=new LinkedHashSet<>(ids);
        var found=books.findByIdInAndPublishedTrueAndDeletedAtIsNull(ordered).stream()
            .collect(Collectors.toMap(Book::getId,b->b));
        var content=stats.findAllById(ordered).stream().collect(Collectors.toMap(BookContentStats::getBookId,v->v));
        var counters=engagement.findAllById(ordered).stream().collect(Collectors.toMap(BookEngagementProjection::getBookId,v->v));
        return ordered.stream().filter(found::containsKey)
            .map(id->Views.book(found.get(id),content.get(id),counters.get(id))).toList();
    }
    public ApiResponses.Page<BookView> list(int page, int limit, Long status, String sort, boolean admin) {
        ApiResponses.validatePage(page, limit);
        var order=CatalogSorts.bookKey(sort);
        Specification<Book> filter=(root, query, cb) -> {
            var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
            conditions.add(cb.isNull(root.get("deletedAt")));
            if (!admin) conditions.add(cb.isTrue(root.get("published")));
            if (status != null) conditions.add(cb.equal(root.get("status").get("id"), status));
            CatalogSorts.orderBooks(root,query,cb,order);
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result=books.findAll(filter, PageRequest.of(page-1, limit));
        var ids=result.getContent().stream().map(Book::getId).toList();
        var content=stats.findAllById(ids).stream().collect(Collectors.toMap(BookContentStats::getBookId, v->v));
        var counters=engagement.findAllById(ids).stream().collect(Collectors.toMap(BookEngagementProjection::getBookId, v->v));
        var data=result.getContent().stream().map(b -> Views.book(b, content.get(b.getId()), counters.get(b.getId()))).toList();
        return new ApiResponses.Page<>(200,true,"Success",data,
            new ApiResponses.Pagination(page,limit,result.getTotalElements(),result.getTotalPages()));
    }
    private void assign(Book book, BookWrite input) {
        book.setAuthor(input.author_id()==null ? null : authors.findById(input.author_id())
            .orElseThrow(() -> ApiException.missing("Author not found")));
        book.setStatus(statuses.findById(input.status_id()).orElseThrow(() -> ApiException.missing("Status not found")));
        var genreIds=new LinkedHashSet<>(input.genre_ids()==null ? List.<Long>of() : input.genre_ids());
        var tagIds=new LinkedHashSet<>(input.tag_ids()==null ? List.<Long>of() : input.tag_ids());
        var selectedGenres=genres.findAllById(genreIds);
        var selectedTags=tags.findAllById(tagIds);
        if (selectedGenres.size()!=genreIds.size() || selectedTags.size()!=tagIds.size())
            throw ApiException.missing("Genre or tag not found");
        book.getGenres().clear(); book.getGenres().addAll(selectedGenres);
        book.getTags().clear(); book.getTags().addAll(selectedTags);
        book.setName(input.name()); book.setSlug(input.slug()); book.setKind(input.kind()); book.setSex(input.sex());
        book.setSynopsis(input.synopsis()); book.setPoster(input.poster()); book.setNote(input.note());
        book.setChapterPerWeek(input.chapter_per_week()==null ? 0 : input.chapter_per_week());
        boolean published=Boolean.TRUE.equals(input.published());
        if (published && !book.isPublished()) book.setPublishedAt(Instant.now());
        if (!published) book.setPublishedAt(null);
        book.setPublished(published);
    }
    @Transactional
    public BookView create(BookWrite input, UUID creator) {
        Book book=new Book(); book.setCreatorId(creator); assign(book,input); books.saveAndFlush(book);
        BookContentStats content=new BookContentStats(); content.setBook(book); stats.save(content);
        BookEngagementProjection counters=new BookEngagementProjection(); counters.setBook(book); engagement.save(counters);
        return view(book);
    }
    @Transactional
    public BookView update(Long id, Map<String,Object> fields) {
        Book book=books.lockById(id).orElseThrow(() -> ApiException.missing("Book not found"));
        BookWrite current=new BookWrite(book.getName(),book.getSlug(),
            book.getAuthor()==null ? null : book.getAuthor().getId(),book.getStatus().getId(),
            book.getKind(),book.getSex(),book.getSynopsis(),book.getPoster(),book.getNote(),
            book.getChapterPerWeek(),book.isPublished(),book.getGenres().stream().map(Genre::getId).toList(),
            book.getTags().stream().map(Tag::getId).toList());
        assign(book,patches.apply(current,fields,BookWrite.class)); books.flush(); return view(book);
    }
    @Transactional
    public BookView updateSlug(String slug, Map<String,Object> fields) {
        return update(books.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> ApiException.missing("Book not found")).getId(),fields);
    }
    @Transactional
    public void delete(Long id) {
        Book book=books.lockById(id).orElseThrow(() -> ApiException.missing("Book not found"));
        book.setDeletedAt(Instant.now()); book.setPublished(false); books.flush();
    }
    @Transactional
    public void deleteSlug(String slug) {
        delete(books.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> ApiException.missing("Book not found")).getId());
    }
}
