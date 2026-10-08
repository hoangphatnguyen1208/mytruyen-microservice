package online.mytruyen.catalog.api;

import online.mytruyen.catalog.service.TopboxesService;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/books/topboxes")
public class TopboxesController {
    private final TopboxesService service;
    public TopboxesController(TopboxesService service) { this.service=service; }
    @GetMapping
    public JsonNode get(@RequestParam int kind,@RequestParam int limit) { return service.get(kind,limit); }
}
