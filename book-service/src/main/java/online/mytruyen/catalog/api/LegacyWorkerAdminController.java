package online.mytruyen.catalog.api;

import jakarta.validation.Valid;
import online.mytruyen.catalog.dto.ApiResponses.Response;
import online.mytruyen.catalog.dto.LegacyWorkerDtos.BindInput;
import online.mytruyen.catalog.service.LegacyWorkerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@ConditionalOnProperty(name="catalog.legacy-worker.enabled",havingValue="true")
@RequestMapping("/api/v1/admin/catalog/worker/books")
public class LegacyWorkerAdminController {
    private final LegacyWorkerService service;
    public LegacyWorkerAdminController(LegacyWorkerService service) { this.service=service; }
    @PostMapping("/{externalId}/bind")
    public Response<Map<String,Object>> bind(@PathVariable long externalId,@RequestBody @Valid BindInput input) {
        return Response.ok(service.bind(externalId,input));
    }
}
