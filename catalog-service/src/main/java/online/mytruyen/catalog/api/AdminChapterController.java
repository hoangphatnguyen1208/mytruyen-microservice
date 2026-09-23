package online.mytruyen.catalog.api;

import online.mytruyen.catalog.dto.ChapterDtos.*;
import online.mytruyen.catalog.dto.ApiResponses;
import online.mytruyen.catalog.dto.ApiResponses.Response;
import online.mytruyen.catalog.service.ChapterService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/catalog/chapters")
public class AdminChapterController {
    private final ChapterService service;
    public AdminChapterController(ChapterService service) { this.service=service; }
    @GetMapping
    public ApiResponses.Page<View> all(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int limit,@RequestParam(required=false) String sort) {
        return service.list(null,null,page,limit,sort,true);
    }
    @GetMapping("/{lookup:id|slug}/{key}")
    public ApiResponses.Page<View> list(@PathVariable String lookup,@PathVariable String key,
            @RequestParam(defaultValue="1") int page,@RequestParam(required=false) Integer limit,
            @RequestParam(required=false) String sort) {
        return service.list(lookup,key,page,limit==null ? (lookup.equals("slug") ? 10 : 30) : limit,sort,true);
    }
    @GetMapping("/{lookup:id|slug}/{key}/{index}")
    public Response<View> detail(@PathVariable String lookup,@PathVariable String key,@PathVariable int index) {
        return Response.ok(service.detail(lookup,key,index,true));
    }
    @GetMapping("/content/{lookup:id|slug}/{key}/{index}")
    public Response<ContentView> content(@PathVariable String lookup,@PathVariable String key,@PathVariable int index) {
        return Response.ok(service.content(lookup,key,index,true));
    }
}
