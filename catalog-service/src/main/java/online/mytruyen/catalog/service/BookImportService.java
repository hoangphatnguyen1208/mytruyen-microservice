package online.mytruyen.catalog.service;

import online.mytruyen.catalog.domain.BookImportMapping;
import online.mytruyen.catalog.dto.ImportDtos.*;
import online.mytruyen.catalog.exception.ApiException;
import online.mytruyen.catalog.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.util.*;

@Service
public class BookImportService {
    private final ImportSourceRepository sources;
    private final BookImportMappingRepository mappings;
    private final BookRepository books;
    private final BookService service;
    private final ObjectMapper json;

    public BookImportService(ImportSourceRepository sources,BookImportMappingRepository mappings,
            BookRepository books,BookService service,ObjectMapper json) {
        this.sources=sources; this.mappings=mappings; this.books=books; this.service=service; this.json=json;
    }
    private void lockSource(String source) {
        var registered=sources.lockByCode(source).orElseThrow(() -> ApiException.missing("Import source not registered"));
        if (!registered.isEnabled()) throw new ApiException(409,"Import source disabled");
    }
    @Transactional
    public Result lookup(String source,String externalId) {
        lockSource(source);
        var mapping=mappings.findBySourceAndExternalId(source,externalId)
            .orElseThrow(() -> ApiException.missing("Import mapping not found"));
        var book=books.lockById(mapping.getBookId()).orElseThrow(() -> new ApiException(409,"Mapped book deleted; manual review required"));
        return new Result(source,externalId,book.getId(),book.getVersion(),
            book.getVersion()!=mapping.getLastBookVersion() ? "manual_review" : "mapped");
    }
    @Transactional
    public Result importBook(String source,String externalId,BookImport input,UUID actor) {
        lockSource(source);
        var existing=mappings.findBySourceAndExternalId(source,externalId);
        String payload=json.writeValueAsString(input.metadata());
        if (existing.isEmpty()) {
            if (input.expected_version()!=null) throw new ApiException(409,"Mapping missing; cannot apply update");
            var created=service.create(input.metadata().asBook(),actor);
            var mapping=new BookImportMapping();
            mapping.setId(UUID.randomUUID()); mapping.setSource(source); mapping.setExternalId(externalId);
            mapping.setBookId(created.id()); mapping.setLastBookVersion(created.version()); mapping.setLastPayload(payload);
            mappings.saveAndFlush(mapping);
            return new Result(source,externalId,created.id(),created.version(),"created");
        }
        var mapping=existing.get();
        var book=books.lockById(mapping.getBookId()).orElseThrow(() -> new ApiException(409,"Mapped book deleted; manual review required"));
        if (book.getVersion()!=mapping.getLastBookVersion())
            throw new ApiException(409,"Book changed outside importer; manual review required");
        if (json.readTree(payload).equals(json.readTree(mapping.getLastPayload())))
            return new Result(source,externalId,book.getId(),book.getVersion(),"unchanged");
        if (!Objects.equals(input.expected_version(),book.getVersion()))
            throw new ApiException(409,"Expected version required and must match; refetch before updating");
        Map<String,Object> fields=json.convertValue(input.metadata(),new TypeReference<Map<String,Object>>() {});
        // No published flag or computed/engagement counters are accepted by this contract.
        var updated=service.update(book.getId(),fields);
        mapping.setLastPayload(payload); mapping.setLastBookVersion(updated.version());
        mappings.saveAndFlush(mapping);
        return new Result(source,externalId,book.getId(),updated.version(),"updated");
    }
}
