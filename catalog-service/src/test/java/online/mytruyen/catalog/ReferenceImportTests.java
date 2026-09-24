package online.mytruyen.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:reference-import;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.username=sa","spring.datasource.password=", "management.health.rabbit.enabled=false"
})
class ReferenceImportTests extends CatalogJwtTestSupport {
    @LocalServerPort int port;
    final HttpClient http=HttpClient.newHttpClient();
    final ObjectMapper json=new ObjectMapper();
    String unique() { return UUID.randomUUID().toString(); }
    String path(String kind,String id) { return "/internal/import/"+kind+"/metruyencv/"+id; }
    JsonNode call(int status,String method,String path,Object body,String role) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path))
            .timeout(java.time.Duration.ofSeconds(20)).header("Content-Type","application/json");
        if (role!=null) request.header("Authorization","Bearer "+token(role));
        request.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
    Map<String,Object> input(String kind) {
        var input=new LinkedHashMap<String,Object>(); input.put("name","Reference");
        if (!kind.equals("authors")) input.put("slug",unique());
        if (kind.equals("tags")) input.put("type","theme");
        return input;
    }
    @Test void eachKindMapsAndReplaysWithoutAssumingSourceIdIsLocalId() throws Exception {
        String external=unique();
        for (String kind:List.of("authors","genres","tags","book-statuses")) {
            var body=input(kind); String path=path(kind,external);
            var first=call(200,"PUT",path,body,"ROLE_IMPORTER");
            assertThat(first.path("outcome").asText()).isEqualTo("created");
            String id=first.path("reference_id").asText();
            assertThat(id).isNotBlank().isNotEqualTo(external);
            assertThat(call(200,"PUT",path,body,"ROLE_IMPORTER").path("reference_id").asText()).isEqualTo(id);
            assertThat(call(200,"GET",path,null,"ROLE_IMPORTER").path("outcome").asText()).isEqualTo("mapped");
            body.put("name","Changed source name");
            call(409,"PUT",path,body,"ROLE_IMPORTER");
        }
    }
    @Test void referencesAreProtectedFromAnonymousUsersAndManualEdits() throws Exception {
        for (String kind:List.of("authors","genres","tags","book-statuses")) {
            var body=input(kind); String path=path(kind,unique());
            call(401,"PUT",path,body,null); call(403,"PUT",path,body,"ROLE_USER");
            call(401,"GET",path,null,null); call(403,"GET",path,null,"ROLE_USER");
            var created=call(200,"PUT",path,body,"ROLE_IMPORTER");
            String adminPath="/"+kind+"/"+(kind.equals("authors") ? created.path("reference_id").asText() : body.get("slug"));
            if (kind.equals("genres")) adminPath="/genres/update/"+created.path("reference_id").asText();
            call(200,"PATCH",adminPath,Map.of("name","Edited by admin"),"ROLE_ADMIN");
            call(409,"PUT",path,body,"ROLE_IMPORTER");
            assertThat(call(200,"GET",path,null,"ROLE_IMPORTER").path("outcome").asText()).isEqualTo("manual_review");
            // Keep provenance; no cascade that would silently allow recreation on next import.
            call(409,"DELETE",kind.equals("genres") ? adminPath.replace("/update/","/delete/") : adminPath,null,"ROLE_ADMIN");
        }
    }
    @Test void namesDoNotMergeAuthorsAndSlugCollisionsRequireReview() throws Exception {
        var author=input("authors");
        String first=call(200,"PUT",path("authors",unique()),author,"ROLE_IMPORTER").path("reference_id").asText();
        assertThat(call(200,"PUT",path("authors",unique()),author,"ROLE_IMPORTER").path("reference_id").asText()).isNotEqualTo(first);
        var genre=input("genres");
        call(200,"PUT",path("genres",unique()),genre,"ROLE_IMPORTER");
        String collision=path("genres",unique());
        call(409,"PUT",collision,genre,"ROLE_IMPORTER"); call(404,"GET",collision,null,"ROLE_IMPORTER");
        call(400,"PUT",path("tags",unique()),input("genres"),"ROLE_IMPORTER");
        call(400,"PUT",path("authors",unique()),Map.of("name","Author","id",unique()),"ROLE_IMPORTER");
    }
    @Test void concurrentAuthorImportCreatesOneMapping() throws Exception {
        String path=path("authors",unique()); var body=input("authors");
        var pool=Executors.newFixedThreadPool(4); var start=new CountDownLatch(1);
        try {
            var tasks=new ArrayList<Future<String>>();
            for (int i=0;i<4;i++) tasks.add(pool.submit(()->{
                start.await(); return call(200,"PUT",path,body,"ROLE_IMPORTER").path("reference_id").asText();
            }));
            start.countDown(); var ids=new HashSet<String>();
            for (var task:tasks) ids.add(task.get(30,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
}
