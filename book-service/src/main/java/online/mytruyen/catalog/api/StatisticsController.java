package online.mytruyen.catalog.api;

import online.mytruyen.catalog.dto.ApiResponses.Response;
import online.mytruyen.catalog.service.StatisticsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StatisticsController {
    private final StatisticsService service;

    public StatisticsController(StatisticsService service) { this.service = service; }

    @GetMapping("/stats/{resource}/count")
    public Response<Long> publicCount(@PathVariable String resource) {
        return Response.ok(service.count(resource, false));
    }

    @GetMapping("/admin/catalog/stats/{resource}/count")
    public Response<Long> adminCount(@PathVariable String resource) {
        return Response.ok(service.count(resource, true));
    }
}
