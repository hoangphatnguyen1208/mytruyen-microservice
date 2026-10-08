package online.mytruyen.catalog.api;

import online.mytruyen.catalog.dto.ApiResponses;

import jakarta.validation.Valid;
import online.mytruyen.catalog.dto.CatalogDtos.*;
import online.mytruyen.catalog.dto.ApiResponses.Response;
import online.mytruyen.catalog.service.BookService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class BookController {
    private final BookService service;
    public BookController(BookService service) { this.service=service; }
    @GetMapping("/books/batch")
    public Response<List<BookView>> batch(@RequestParam List<Long> ids) { return Response.ok(service.batch(ids)); }
    @GetMapping("/books")
    public ApiResponses.Page<BookView> list(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int limit, @RequestParam(required=false) Long status,
            @RequestParam(defaultValue="-created_at") String sort) {
        return service.list(page,limit,status,sort,false);
    }
    @GetMapping("/admin/catalog/books")
    public ApiResponses.Page<BookView> adminList(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int limit, @RequestParam(required=false) Long status,
            @RequestParam(defaultValue="-created_at") String sort) {
        return service.list(page,limit,status,sort,true);
    }
    @GetMapping("/books/id/{id}")
    public Response<BookView> detail(@PathVariable Long id) { return Response.ok(service.detail(id,false)); }
    @GetMapping("/admin/catalog/books/id/{id}")
    public Response<BookView> adminDetail(@PathVariable Long id) { return Response.ok(service.detail(id,true)); }
    @GetMapping("/books/slug/{slug}")
    public Response<BookView> slug(@PathVariable String slug) { return Response.ok(service.bySlug(slug)); }
    @PostMapping("/books") @ResponseStatus(HttpStatus.CREATED)
    public Response<BookView> create(@Valid @RequestBody BookWrite input, @AuthenticationPrincipal UUID creator) {
        return Response.created(service.create(input,creator));
    }
    @PatchMapping("/books/id/{id}")
    public Response<BookView> update(@PathVariable Long id,@RequestBody Map<String,Object> fields) {
        return Response.ok(service.update(id,fields));
    }
    @PatchMapping("/books/slug/{slug}")
    public Response<BookView> updateSlug(@PathVariable String slug,@RequestBody Map<String,Object> fields) {
        return Response.ok(service.updateSlug(slug,fields));
    }
    @DeleteMapping("/books/id/{id}")
    public Response<Void> delete(@PathVariable Long id) { service.delete(id); return Response.ok(null); }
    @DeleteMapping("/books/slug/{slug}")
    public Response<Void> deleteSlug(@PathVariable String slug) { service.deleteSlug(slug); return Response.ok(null); }
}
