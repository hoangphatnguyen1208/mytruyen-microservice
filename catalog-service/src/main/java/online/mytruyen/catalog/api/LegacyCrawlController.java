package online.mytruyen.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import online.mytruyen.catalog.dto.ApiResponses.Response;
import online.mytruyen.catalog.messaging.LegacyCrawlPublisher;
import static online.mytruyen.catalog.messaging.LegacyCrawlPublisher.Command.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rabbitmq")
public class LegacyCrawlController {
    public record BookRequest(@NotNull @Positive Long book_id) {}
    private final LegacyCrawlPublisher publisher;
    public LegacyCrawlController(LegacyCrawlPublisher publisher) { this.publisher=publisher; }
    @PostMapping("/{command:genres|tags|book-statuses|all-books}")
    public Response<Void> enqueue(@PathVariable String command) {
        publisher.publish(switch(command) {
            case "genres" -> GENRES; case "tags" -> TAGS;
            case "book-statuses" -> STATUSES; case "all-books" -> ALL_BOOKS;
            default -> throw new IllegalArgumentException("Unsupported command");
        },null);
        return Response.ok(null);
    }
    @PostMapping("/book")
    public Response<Void> book(@RequestBody @Valid BookRequest request) {
        publisher.publish(BOOK,request.book_id()); return Response.ok(null);
    }
    @PostMapping("/chapters")
    public Response<Void> chapters(@RequestBody @Valid BookRequest request) {
        publisher.publish(CHAPTERS,request.book_id()); return Response.ok(null);
    }
}
