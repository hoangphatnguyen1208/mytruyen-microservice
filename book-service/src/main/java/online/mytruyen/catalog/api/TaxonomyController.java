package online.mytruyen.catalog.api;


import online.mytruyen.catalog.service.TaxonomyService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.*;
import static online.mytruyen.catalog.dto.ApiResponses.*;
import static online.mytruyen.catalog.dto.CatalogDtos.*;

@RestController @RequestMapping("/api/v1")
public class TaxonomyController {
    private final TaxonomyService service;
    public TaxonomyController(TaxonomyService service) { this.service=service; }
    @GetMapping("/{kind:genres|tags|book-statuses}")
    public Response<List<TaxonView>> list(@PathVariable String kind,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="100") int limit) {
        return Response.ok(service.list(kind,page,limit));
    }
    @GetMapping("/{kind:genres|tags|book-statuses}/{slug}")
    public Response<TaxonView> get(@PathVariable String kind,@PathVariable String slug) { return Response.ok(service.get(kind,slug)); }
    @PostMapping("/{kind:genres|tags|book-statuses}") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public Response<TaxonView> create(@PathVariable String kind,@Valid @RequestBody TaxonWrite input) { return Response.created(service.create(kind,input)); }
    @PatchMapping("/{kind:tags|book-statuses}/{slug}") @PreAuthorize("hasRole('ADMIN')")
    public Response<TaxonView> update(@PathVariable String kind,@PathVariable String slug,@RequestBody Map<String,Object> input) { return Response.ok(service.update(kind,slug,input)); }
    @DeleteMapping("/{kind:tags|book-statuses}/{slug}") @PreAuthorize("hasRole('ADMIN')")
    public Response<Void> delete(@PathVariable String kind,@PathVariable String slug) { service.delete(kind,slug); return Response.ok(null); }
    @PatchMapping("/genres/update/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Response<TaxonView> updateGenre(@PathVariable Long id,@RequestBody Map<String,Object> input) { return Response.ok(service.updateGenre(id,input)); }
    @DeleteMapping("/genres/delete/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Response<Void> deleteGenre(@PathVariable Long id) { service.deleteGenre(id); return Response.ok(null); }

    @GetMapping("/authors")
    public Response<List<AuthorView>> authors(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int limit) {
        return Response.ok(service.authors(page,limit));
    }
    @GetMapping("/authors/id/{id}")
    public Response<AuthorView> author(@PathVariable UUID id) { return Response.ok(service.author(id)); }
    @GetMapping("/authors/{name}")
    public Response<AuthorView> authorByName(@PathVariable String name) { return Response.ok(service.authorByName(name)); }
    @PostMapping("/authors") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public Response<AuthorView> createAuthor(@Valid @RequestBody AuthorWrite input) { return Response.created(service.createAuthor(input)); }
    @PatchMapping("/authors/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Response<AuthorView> updateAuthor(@PathVariable UUID id,@RequestBody Map<String,Object> input) { return Response.ok(service.updateAuthor(id,input)); }
    @DeleteMapping("/authors/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Response<Void> deleteAuthor(@PathVariable UUID id) { service.deleteAuthor(id); return Response.ok(null); }
}
