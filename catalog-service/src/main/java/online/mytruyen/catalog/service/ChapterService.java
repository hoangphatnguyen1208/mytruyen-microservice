package online.mytruyen.catalog.service;

import online.mytruyen.catalog.domain.*;
import online.mytruyen.catalog.dto.ChapterDtos.*;
import online.mytruyen.catalog.dto.ApiResponses;
import online.mytruyen.catalog.exception.ApiException;
import online.mytruyen.catalog.mapper.ChapterViews;
import online.mytruyen.catalog.repository.*;
import online.mytruyen.catalog.support.Patches;
import online.mytruyen.catalog.support.CatalogSorts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly=true)
public class ChapterService {
    private static final Pattern WORDS=Pattern.compile("\\S+",Pattern.UNICODE_CHARACTER_CLASS);
    private final BookRepository books;
    private final ChapterRepository chapters;
    private final ChapterContentRepository contents;
    private final Patches patches;
    private final ChapterChanges changes;
    public ChapterService(BookRepository books, ChapterRepository chapters, ChapterContentRepository contents, Patches patches, ChapterChanges changes) {
        this.books=books; this.chapters=chapters; this.contents=contents; this.patches=patches;
        this.changes=changes;
    }
    private Book book(String lookup,String key,boolean admin) {
        Book b;
        if ("id".equals(lookup)) {
            long id;
            try { id=Long.parseLong(key); } catch (NumberFormatException e) { throw new ApiException(400,"Invalid book ID"); }
            b=books.findByIdAndDeletedAtIsNull(id).orElseThrow(ApiException::missing);
        } else b=books.findBySlugAndDeletedAtIsNull(key).orElseThrow(ApiException::missing);
        if (!admin && !b.isPublished()) throw ApiException.missing();
        return b;
    }
    private Chapter chapter(Book b,int index,boolean admin) {
        if (index<1) throw new ApiException(400,"Chapter index must be positive");
        Chapter c=chapters.findByBookIdAndChapterIndexAndDeletedAtIsNull(b.getId(),index).orElseThrow(ApiException::missing);
        if (!admin && !c.isPublished()) throw ApiException.missing();
        return c;
    }
    public View detail(String lookup,String key,int index,boolean admin) {
        return ChapterViews.chapter(chapter(book(lookup,key,admin),index,admin));
    }
    public ContentView content(String lookup,String key,int index,boolean admin) {
        Chapter c=chapter(book(lookup,key,admin),index,admin);
        return ChapterViews.content(contents.findById(c.getId()).orElseThrow(ApiException::missing));
    }
    public ApiResponses.Page<View> list(String lookup,String key,int page,int limit,String sort,boolean admin) {
        ApiResponses.validatePage(page,limit);
        Long bookId=key==null ? null : book(lookup,key,admin).getId();
        Sort ordering=CatalogSorts.chapter(sort,bookId==null);
        Specification<Chapter> filter=(root,query,cb)-> {
            var rules=new ArrayList<jakarta.persistence.criteria.Predicate>();
            rules.add(cb.isNull(root.get("deletedAt")));
            rules.add(cb.isNull(root.get("book").get("deletedAt")));
            if (bookId!=null) rules.add(cb.equal(root.get("book").get("id"),bookId));
            if (!admin) {
                rules.add(cb.isTrue(root.get("published")));
                rules.add(cb.isTrue(root.get("book").get("published")));
            }
            return cb.and(rules.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result=chapters.findAll(filter,PageRequest.of(page-1,limit,ordering));
        return new ApiResponses.Page<>(200,true,"Success",result.map(ChapterViews::chapter).getContent(),
            new ApiResponses.Pagination(page,limit,result.getTotalElements(),result.getTotalPages()));
    }
    private Book lockBook(Long id) { return books.lockById(id).orElseThrow(ApiException::missing); }
    // Every writer locks parent before child. The initial lookup is scalar, not a managed stale chapter.
    private Chapter lock(Long id) {
        Long bookId=chapters.findBookId(id).orElseThrow(ApiException::missing);
        lockBook(bookId);
        Chapter c=chapters.lockById(id).orElseThrow(ApiException::missing);
        return c;
    }
    private Chapter lock(String lookup,String key,int index) {
        Book b=lockBook(book(lookup,key,true).getId());
        Chapter c=chapter(b,index,true);
        c=chapters.lockById(c.getId()).orElseThrow(ApiException::missing);
        return c;
    }
    @Transactional
    public View create(String lookup,String key,Write input,UUID creator) {
        Book b=lockBook(book(lookup,key,true).getId());
        Chapter c=new Chapter(); c.setBook(b); c.setCreatorId(creator);
        c.setChapterIndex(input.index()); c.setName(input.name());
        chapters.saveAndFlush(c);
        changes.record(c,"ChapterCreated");
        return ChapterViews.chapter(c);
    }
    @Transactional
    public View update(Long id,Map<String,Object> fields) {
        Chapter c=lock(id);
        MetadataWrite next=patches.apply(new MetadataWrite(c.getChapterIndex(),c.getName(),c.isPublished()),fields,MetadataWrite.class);
        boolean transition=c.isPublished()!=next.published();
        c.setName(next.name()); c.setChapterIndex(next.index()); c.setUpdatedAt(Instant.now());
        if (transition) publication(c,next.published());
        changes.record(c,transition ? (next.published() ? "ChapterPublished" : "ChapterUnpublished") : "ChapterUpdated");
        return ChapterViews.chapter(c);
    }
    @Transactional
    public void delete(Long id) {
        Chapter c=lock(id); c.setDeletedAt(Instant.now()); c.setPublished(false); c.setPublishedAt(null);
        changes.record(c,"ChapterDeleted");
    }
    @Transactional
    public View publish(Long id) {
        Chapter c=lock(id);
        if (!c.isPublished()) {
            publication(c,true);
            changes.record(c,"ChapterPublished");
        }
        return ChapterViews.chapter(c);
    }
    @Transactional
    public View unpublish(Long id) {
        Chapter c=lock(id);
        if (c.isPublished()) {
            publication(c,false);
            changes.record(c,"ChapterUnpublished");
        }
        return ChapterViews.chapter(c);
    }
    private void publication(Chapter c,boolean published) {
        if (published) {
            ChapterContent content=contents.findById(c.getId()).orElseThrow(()->new ApiException(409,"Content required before publication"));
            if (content.getContent().isBlank()) throw new ApiException(409,"Nonblank content required before publication");
            assign(c,content,content.getContent());
            c.setPublishedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        } else c.setPublishedAt(null);
        c.setPublished(published);
    }
    private void assign(Chapter c,ChapterContent content,String text) {
        content.setContent(text);
        try { content.setContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        c.setWordCount(WORDS.matcher(text).results().count());
        // Content-only writes also advance the chapter aggregate version.
        c.setUpdatedAt(Instant.now());
    }
    @Transactional
    public ContentView createContent(String lookup,String key,int index,ContentWrite input) {
        Chapter c=lock(lookup,key,index);
        if(contents.existsById(c.getId())) throw new ApiException(409,"Chapter content already exists");
        ChapterContent content=new ChapterContent(); content.setChapter(c); assign(c,content,input.content());
        contents.saveAndFlush(content); changes.record(c,"ChapterContentCreated");
        return ChapterViews.content(content);
    }
    @Transactional
    public ContentView updateContent(String lookup,String key,int index,Map<String,Object> fields) {
        Chapter c=lock(lookup,key,index);
        ChapterContent content=contents.findById(c.getId()).orElseThrow(ApiException::missing);
        ContentWrite next=patches.apply(new ContentWrite(content.getContent()),fields,ContentWrite.class);
        assign(c,content,next.content()); contents.flush(); changes.record(c,"ChapterContentUpdated");
        return ChapterViews.content(content);
    }
    @Transactional
    public void deleteContent(String lookup,String key,int index) {
        Chapter c=lock(lookup,key,index);
        if (c.isPublished()) throw new ApiException(409,"Unpublish chapter before deleting its content");
        ChapterContent content=contents.findById(c.getId()).orElseThrow(ApiException::missing);
        contents.delete(content); c.setWordCount(0); c.setUpdatedAt(Instant.now()); contents.flush();
        changes.record(c,"ChapterContentDeleted");
    }
}
