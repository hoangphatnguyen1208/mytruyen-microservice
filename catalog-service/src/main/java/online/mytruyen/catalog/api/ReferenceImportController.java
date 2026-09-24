package online.mytruyen.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import online.mytruyen.catalog.dto.ApiResponses.Response;
import online.mytruyen.catalog.dto.CatalogDtos.*;
import online.mytruyen.catalog.service.ReferenceImportService;
import online.mytruyen.catalog.service.ReferenceImportService.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/import")
public class ReferenceImportController {
    private final ReferenceImportService service;
    public ReferenceImportController(ReferenceImportService service) { this.service=service; }
    @GetMapping("/{kind:authors|genres|tags|book-statuses}/{source}/{externalId}")
    public Response<Result> lookup(@PathVariable String kind,
            @PathVariable @Pattern(regexp="[a-z0-9][a-z0-9_-]{0,49}") String source,
            @PathVariable @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,149}") String externalId) {
        return Response.ok(service.lookup(source,kind,externalId));
    }
    @PutMapping("/authors/{source}/{externalId}")
    public Response<Result> author(
            @PathVariable @Pattern(regexp="[a-z0-9][a-z0-9_-]{0,49}") String source,
            @PathVariable @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,149}") String externalId,
            @RequestBody @Valid AuthorWrite input) {
        return Response.ok(service.author(source,externalId,input));
    }
    @PutMapping("/{kind:genres|tags|book-statuses}/{source}/{externalId}")
    public Response<Result> taxon(@PathVariable String kind,
            @PathVariable @Pattern(regexp="[a-z0-9][a-z0-9_-]{0,49}") String source,
            @PathVariable @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,149}") String externalId,
            @RequestBody @Valid TaxonWrite input) {
        return Response.ok(service.taxon(source,kind,externalId,input));
    }
}
