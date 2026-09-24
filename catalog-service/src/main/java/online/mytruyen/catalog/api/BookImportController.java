package online.mytruyen.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import online.mytruyen.catalog.dto.ApiResponses.Response;
import online.mytruyen.catalog.dto.ImportDtos.*;
import online.mytruyen.catalog.service.BookImportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/import/books/{source}/{externalId}")
public class BookImportController {
    private final BookImportService service;
    public BookImportController(BookImportService service) { this.service=service; }
    @GetMapping
    public Response<Result> lookup(
            @PathVariable @Pattern(regexp="[a-z0-9][a-z0-9_-]{0,49}") String source,
            @PathVariable @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,149}") String externalId) {
        return Response.ok(service.lookup(source,externalId));
    }
    @PutMapping
    public Response<Result> importBook(
            @PathVariable @Pattern(regexp="[a-z0-9][a-z0-9_-]{0,49}") String source,
            @PathVariable @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,149}") String externalId,
            @RequestBody @Valid BookImport input,@AuthenticationPrincipal UUID actor) {
        return Response.ok(service.importBook(source,externalId,input,actor));
    }
}
