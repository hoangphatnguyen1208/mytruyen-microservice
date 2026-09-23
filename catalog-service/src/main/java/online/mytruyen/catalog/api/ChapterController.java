package online.mytruyen.catalog.api;

import jakarta.validation.Valid;
import online.mytruyen.catalog.dto.ChapterDtos.*;
import online.mytruyen.catalog.dto.ApiResponses;
import online.mytruyen.catalog.dto.ApiResponses.Response;
import online.mytruyen.catalog.service.ChapterService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/chapters")
public class ChapterController {
    private final ChapterService service;
    public ChapterController(ChapterService service) { this.service=service; }
    @GetMapping
    public ApiResponses.Page<View> all(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int limit,@RequestParam(defaultValue="index") String sort) {
        return service.list(null,null,page,limit,sort,false);
    }
    @GetMapping("/{lookup:id|slug}/{key}")
    public ApiResponses.Page<View> list(@PathVariable String lookup,@PathVariable String key,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="30") int limit,
            @RequestParam(defaultValue="index") String sort) {
        return service.list(lookup,key,page,limit,sort,false);
    }
    @GetMapping("/{lookup:id|slug}/{key}/{index}")
    public Response<View> detail(@PathVariable String lookup,@PathVariable String key,@PathVariable int index) {
        return Response.ok(service.detail(lookup,key,index,false));
    }
    @PostMapping("/{lookup:id|slug}/{key}") @ResponseStatus(HttpStatus.CREATED)
    public Response<View> create(@PathVariable String lookup,@PathVariable String key,
            @Valid @RequestBody Write input,@AuthenticationPrincipal UUID creator) {
        return Response.created(service.create(lookup,key,input,creator));
    }
    @PatchMapping("/id/{id}")
    public Response<View> update(@PathVariable Long id,@RequestBody Map<String,Object> input) {
        return Response.ok(service.update(id,input));
    }
    @PostMapping("/id/{id}/publish")
    public Response<View> publish(@PathVariable Long id) { return Response.ok(service.publish(id)); }
    @PostMapping("/id/{id}/unpublish")
    public Response<View> unpublish(@PathVariable Long id) { return Response.ok(service.unpublish(id)); }
    // Legacy /slug/{id} DELETE also takes a chapter ID, not a book slug.
    @DeleteMapping({"/id/{id}","/slug/{id}"})
    public Response<Void> delete(@PathVariable Long id) { service.delete(id); return Response.ok(null); }
    @GetMapping("/content/{lookup:id|slug}/{key}/{index}")
    public Response<ContentView> content(@PathVariable String lookup,@PathVariable String key,@PathVariable int index) {
        return Response.ok(service.content(lookup,key,index,false));
    }
    @PostMapping("/content/{lookup:id|slug}/{key}/{index}") @ResponseStatus(HttpStatus.CREATED)
    public Response<ContentView> createContent(@PathVariable String lookup,@PathVariable String key,@PathVariable int index,
            @Valid @RequestBody ContentWrite input) {
        return Response.created(service.createContent(lookup,key,index,input));
    }
    @PatchMapping("/content/{lookup:id|slug}/{key}/{index}")
    public Response<ContentView> updateContent(@PathVariable String lookup,@PathVariable String key,@PathVariable int index,
            @RequestBody Map<String,Object> input) {
        return Response.ok(service.updateContent(lookup,key,index,input));
    }
    @DeleteMapping("/content/{lookup:id|slug}/{key}/{index}")
    public Response<Void> deleteContent(@PathVariable String lookup,@PathVariable String key,@PathVariable int index) {
        service.deleteContent(lookup,key,index); return Response.ok(null);
    }
}
