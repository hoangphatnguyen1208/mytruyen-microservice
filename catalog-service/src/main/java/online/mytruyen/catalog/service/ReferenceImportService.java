package online.mytruyen.catalog.service;

import jakarta.persistence.*;
import online.mytruyen.catalog.domain.*;
import online.mytruyen.catalog.repository.*;
import online.mytruyen.catalog.exception.ApiException;
import online.mytruyen.catalog.dto.CatalogDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class ReferenceImportService {
    public record Result(String source,String external_id,String kind,String reference_id,String outcome) {}
    private final ImportSourceRepository sources;
    private final ReferenceImportMappingRepository mappings;
    private final TaxonomyService taxonomy;
    private final EntityManager entities;
    private final ObjectMapper json;
    public ReferenceImportService(ImportSourceRepository sources,ReferenceImportMappingRepository mappings,
            TaxonomyService taxonomy,EntityManager entities,ObjectMapper json) {
        this.sources=sources; this.mappings=mappings; this.taxonomy=taxonomy; this.entities=entities; this.json=json;
    }
    private void lockSource(String code) {
        var source=sources.lockByCode(code).orElseThrow(()->ApiException.missing("Import source not registered"));
        if (!source.isEnabled()) throw new ApiException(409,"Import source disabled");
    }
    private Result result(ReferenceImportMapping m,String outcome) {
        return new Result(m.getSource(),m.getExternalId(),m.getKind(),m.referenceId(),outcome);
    }
    private Object current(ReferenceImportMapping m) {
        // A shared lock prevents admin edits while comparing the stored snapshot.
        return switch(m.getKind()) {
            case "authors" -> {
                var a=entities.find(Author.class,m.getAuthorId(),LockModeType.PESSIMISTIC_READ);
                if (a==null) throw new ApiException(409,"Mapped author missing");
                yield new AuthorWrite(a.getName(),a.getLocalName(),a.getAvatarUrl());
            }
            case "genres" -> {
                var g=entities.find(Genre.class,m.getGenreId(),LockModeType.PESSIMISTIC_READ);
                if (g==null) throw new ApiException(409,"Mapped genre missing");
                yield new TaxonWrite(g.getName(),g.getSlug(),g.getDescription(),null);
            }
            case "tags" -> {
                var t=entities.find(Tag.class,m.getTagId(),LockModeType.PESSIMISTIC_READ);
                if (t==null) throw new ApiException(409,"Mapped tag missing");
                yield new TaxonWrite(t.getName(),t.getSlug(),t.getDescription(),t.getType());
            }
            case "book-statuses" -> {
                var s=entities.find(BookStatus.class,m.getStatusId(),LockModeType.PESSIMISTIC_READ);
                if (s==null) throw new ApiException(409,"Mapped status missing");
                yield new TaxonWrite(s.getName(),s.getSlug(),s.getDescription(),null);
            }
            default -> throw ApiException.missing();
        };
    }
    private boolean same(Object value,String stored) {
        return json.valueToTree(value).equals(json.readTree(stored));
    }
    @Transactional
    public Result lookup(String source,String kind,String externalId) {
        lockSource(source);
        var m=mappings.findBySourceAndKindAndExternalId(source,kind,externalId).orElseThrow(ApiException::missing);
        return result(m,same(current(m),m.getPayload()) ? "mapped" : "manual_review");
    }
    @Transactional
    public Result author(String source,String externalId,AuthorWrite input) {
        return save(source,"authors",externalId,input);
    }
    @Transactional
    public Result taxon(String source,String kind,String externalId,TaxonWrite input) {
        return save(source,kind,externalId,input);
    }
    private Result save(String source,String kind,String externalId,Object input) {
        lockSource(source);
        var existing=mappings.findBySourceAndKindAndExternalId(source,kind,externalId);
        if (existing.isPresent()) {
            var m=existing.get();
            // References are shared by many books: no automatic rename/merge in this phase.
            if (!same(input,m.getPayload()) || !same(current(m),m.getPayload()))
                throw new ApiException(409,"Reference changed; manual review required");
            return result(m,"unchanged");
        }
        var m=new ReferenceImportMapping(); m.setId(UUID.randomUUID()); m.setSource(source);
        m.setKind(kind); m.setExternalId(externalId); m.setPayload(json.writeValueAsString(input));
        if (kind.equals("authors")) m.setAuthorId(taxonomy.createAuthor((AuthorWrite)input).id());
        else {
            long id=taxonomy.create(kind,(TaxonWrite)input).id();
            switch(kind) {
                case "genres" -> m.setGenreId(id);
                case "tags" -> m.setTagId(id);
                case "book-statuses" -> m.setStatusId(id);
                default -> throw ApiException.missing();
            }
        }
        mappings.saveAndFlush(m);
        return result(m,"created");
    }
}
