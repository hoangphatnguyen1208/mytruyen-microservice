package online.mytruyen.catalog.api;

import jakarta.validation.Valid;
import online.mytruyen.catalog.dto.ApiResponses.Response;
import online.mytruyen.catalog.dto.CatalogDtos.*;
import online.mytruyen.catalog.dto.LegacyWorkerDtos.*;
import online.mytruyen.catalog.service.LegacyWorkerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@ConditionalOnProperty(name="catalog.legacy-worker.enabled",havingValue="true")
@RequestMapping("/api/v1/worker")
public class LegacyWorkerController {
    private final LegacyWorkerService service;
    public LegacyWorkerController(LegacyWorkerService service) { this.service=service; }
    @GetMapping("/books/id/{id}")
    public Response<Map<String,Object>> book(@PathVariable long id) { return Response.ok(service.detail(id)); }
    @PostMapping("/books") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> create(@RequestBody Map<String,Object> input,@AuthenticationPrincipal UUID creator) {
        return Response.created(service.create(input,creator));
    }
    @PatchMapping("/books/id/{id}")
    public Response<Map<String,Object>> update(@PathVariable long id,@RequestBody Map<String,Object> input) { return Response.ok(service.update(id,input)); }
    @GetMapping("/authors/{name}")
    public Response<AuthorView> author(@PathVariable String name) { return Response.ok(service.author(name)); }
    @PostMapping("/authors") @ResponseStatus(HttpStatus.CREATED)
    public Response<AuthorView> createAuthor(@RequestBody @Valid AuthorWrite input) { return Response.created(service.createAuthor(input)); }
    @GetMapping("/{kind:genres|tags|book-statuses}/{slug}")
    public Response<TaxonView> taxon(@PathVariable String kind,@PathVariable String slug) { return Response.ok(service.taxon(kind,slug)); }
    @PostMapping("/{kind:genres|tags|book-statuses}") @ResponseStatus(HttpStatus.CREATED)
    public Response<TaxonView> createTaxon(@PathVariable String kind,@RequestBody @Valid TaxonWrite input) { return Response.created(service.createTaxon(kind,input)); }
    @GetMapping("/chapters/id/{book}/{index}")
    public Response<Map<String,Object>> chapter(@PathVariable long book,@PathVariable int index) { return Response.ok(service.chapter(book,index)); }
    @PostMapping("/chapters/id/{book}") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> createChapter(@PathVariable long book,@RequestBody @Valid ChapterInput input,@AuthenticationPrincipal UUID creator) {
        return Response.created(service.createChapter(book,input,creator));
    }
}
