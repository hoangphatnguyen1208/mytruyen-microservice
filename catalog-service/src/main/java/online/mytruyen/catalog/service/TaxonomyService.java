package online.mytruyen.catalog.service;

import online.mytruyen.catalog.dto.ApiResponses;
import online.mytruyen.catalog.exception.ApiException;
import online.mytruyen.catalog.mapper.Views;
import online.mytruyen.catalog.support.Patches;

import online.mytruyen.catalog.domain.*;
import online.mytruyen.catalog.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.util.*;
import static online.mytruyen.catalog.dto.CatalogDtos.*;

@Service @Transactional(readOnly = true)
public class TaxonomyService {
    private final GenreRepository genres;
    private final TagRepository tags;
    private final BookStatusRepository statuses;
    private final AuthorRepository authors;
    private final Patches patches;
    public TaxonomyService(GenreRepository genres, TagRepository tags, BookStatusRepository statuses, AuthorRepository authors, Patches patches) {
        this.genres=genres; this.tags=tags; this.statuses=statuses; this.authors=authors; this.patches=patches;
    }
    public List<TaxonView> list(String kind, int page, int limit) {
        ApiResponses.validatePage(page,limit);
        var pageable=PageRequest.of(page-1,limit,Sort.by("id"));
        return switch(kind) {
            case "genres" -> genres.findAll(pageable).stream().map(Views::taxon).toList();
            case "tags" -> tags.findAll(pageable).stream().map(Views::taxon).toList();
            case "book-statuses" -> statuses.findAll(pageable).stream().map(Views::taxon).toList();
            default -> throw ApiException.missing();
        };
    }
    private AuditedEntity find(String kind,String slug) {
        return switch(kind) {
            case "genres" -> genres.findBySlug(slug).orElseThrow(ApiException::missing);
            case "tags" -> tags.findBySlug(slug).orElseThrow(ApiException::missing);
            case "book-statuses" -> statuses.findBySlug(slug).orElseThrow(ApiException::missing);
            default -> throw ApiException.missing();
        };
    }
    public TaxonView get(String kind,String slug) { return Views.taxon(find(kind,slug)); }

    private void assign(AuditedEntity value,TaxonWrite input) {
        if (value instanceof Tag && (input.type()==null || input.type().isBlank())) throw new ApiException(400,"Tag type required");
        if (!(value instanceof Tag) && input.type()!=null) throw new ApiException(400,"type is only supported for tags");
        if (value instanceof BookStatus && (input.name().length()>100 || input.slug().length()>100)) throw new ApiException(400,"Status name/slug maximum is 100");
        if (value instanceof Genre g) { g.setName(input.name()); g.setSlug(input.slug()); g.setDescription(input.description()); }
        else if (value instanceof Tag t) { t.setName(input.name()); t.setSlug(input.slug()); t.setDescription(input.description()); t.setType(input.type()); }
        else if (value instanceof BookStatus s) { s.setName(input.name()); s.setSlug(input.slug()); s.setDescription(input.description()); }
    }
    private AuditedEntity save(AuditedEntity value) {
        if (value instanceof Genre g) return genres.saveAndFlush(g);
        if (value instanceof Tag t) return tags.saveAndFlush(t);
        return statuses.saveAndFlush((BookStatus)value);
    }
    @Transactional
    public TaxonView create(String kind,TaxonWrite input) {
        AuditedEntity value=switch(kind) {
            case "genres" -> new Genre(); case "tags" -> new Tag(); case "book-statuses" -> new BookStatus();
            default -> throw ApiException.missing();
        };
        assign(value,input);
        return Views.taxon(save(value));
    }
    private TaxonView patch(AuditedEntity value,Map<String,Object> fields) {
        var dto=Views.taxon(value);
        var merged=patches.apply(new TaxonWrite(dto.name(),dto.slug(),dto.description(),dto.type()),fields,TaxonWrite.class);
        assign(value,merged);
        return Views.taxon(save(value));
    }
    @Transactional
    public TaxonView update(String kind,String slug,Map<String,Object> fields) { return patch(find(kind,slug),fields); }
    @Transactional
    public TaxonView updateGenre(Long id,Map<String,Object> fields) { return patch(genres.findById(id).orElseThrow(ApiException::missing),fields); }

    private void remove(AuditedEntity value) {
        if (value instanceof Genre g) { genres.delete(g); genres.flush(); }
        else if (value instanceof Tag t) { tags.delete(t); tags.flush(); }
        else { statuses.delete((BookStatus)value); statuses.flush(); }
    }
    @Transactional
    public void delete(String kind,String slug) { remove(find(kind,slug)); }
    @Transactional
    public void deleteGenre(Long id) { remove(genres.findById(id).orElseThrow(ApiException::missing)); }

    public List<AuthorView> authors(int page,int limit) {
        ApiResponses.validatePage(page,limit);
        return authors.findAll(PageRequest.of(page-1,limit,Sort.by("name","id"))).stream().map(Views::author).toList();
    }
    public AuthorView author(UUID id) { return Views.author(authors.findById(id).orElseThrow(ApiException::missing)); }
    public AuthorView authorByName(String name) {
        var found=authors.findByName(name);
        if (found.isEmpty()) throw ApiException.missing();
        if (found.size()>1) throw new ApiException(409,"Multiple authors share this name; use /authors/id/{id}");
        return Views.author(found.get(0));
    }
    @Transactional
    public AuthorView createAuthor(AuthorWrite input) {
        Author a=new Author(); assignAuthor(a,input);
        return Views.author(authors.saveAndFlush(a));
    }
    private void assignAuthor(Author a,AuthorWrite input) { a.setName(input.name()); a.setLocalName(input.local_name()); a.setAvatarUrl(input.avatar()); }
    @Transactional
    public AuthorView updateAuthor(UUID id,Map<String,Object> fields) {
        var a=authors.findById(id).orElseThrow(ApiException::missing);
        assignAuthor(a,patches.apply(new AuthorWrite(a.getName(),a.getLocalName(),a.getAvatarUrl()),fields,AuthorWrite.class));
        return Views.author(authors.saveAndFlush(a));
    }
    @Transactional
    public void deleteAuthor(UUID id) { authors.delete(authors.findById(id).orElseThrow(ApiException::missing)); authors.flush(); }
}
