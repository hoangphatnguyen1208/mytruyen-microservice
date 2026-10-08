package online.mytruyen.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:legacy-worker;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.username=sa","spring.datasource.password=","management.health.rabbit.enabled=false",
    "catalog.legacy-worker.enabled=true","catalog.legacy-worker.queue=test-only",
    "catalog.legacy-worker.status-map={\"9\":\"compat-status\"}"
})
class LegacyWorkerApiTests extends CatalogJwtTestSupport {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    final HttpClient http=HttpClient.newHttpClient(); final ObjectMapper json=new ObjectMapper();
    JsonNode call(int status,String method,String path,Object body,String role) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).header("Content-Type","application/json");
        if (role!=null) req.header("Authorization","Bearer "+token(role));
        req.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=http.send(req.build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
    Map<String,Object> book(long id) throws Exception {
        call(201,"POST","/worker/book-statuses",Map.of("name","Compat status","slug","compat-status"),"ROLE_IMPORTER");
        var value=new LinkedHashMap<String,Object>(); value.put("id",id); value.put("name","Source book");
        value.put("slug","source-"+id); value.put("kind",1); value.put("sex",1); value.put("status_id",9);
        value.put("synopsis",""); value.put("poster",""); value.put("note",List.of()); value.put("published",true);
        value.put("chapter_count",100); value.put("word_count",25000); value.put("view_count",1000);
        value.put("average_rating",4.25); return value;
    }
    @Test void legacyPayloadAndPatchPreserveVisibleSourceIdsCountersAndPublication() throws Exception {
        var input=book(900001); var created=call(201,"POST","/worker/books",input,"ROLE_IMPORTER");
        assertThat(created.path("id").asLong()).isEqualTo(900001);
        assertThat(created.path("published").asBoolean()).isTrue();
        assertThat(created.path("chapter_count").asLong()).isEqualTo(100);
        assertThat(created.path("average_rating").asDouble()).isEqualTo(4.25);
        call(201,"POST","/worker/books",input,"ROLE_IMPORTER");
        var updated=call(200,"PATCH","/worker/books/id/900001",Map.of("name","Updated","view_count",1234,"chapter_count",101),"ROLE_IMPORTER");
        assertThat(updated.path("view_count").asLong()).isEqualTo(1234);
        assertThat(updated.path("chapter_count").asLong()).isEqualTo(101);
        assertThat(updated.path("name").asText()).isEqualTo("Updated");
        long internal=db.queryForObject("select book_id from legacy_worker_books where external_id=900001",Long.class);
        assertThat(internal).isNotEqualTo(900001);
        assertThat(call(200,"GET","/books/id/"+internal,null,null).path("view_count").asLong()).isZero();
        call(400,"PATCH","/worker/books/id/900001",Map.of("id",555),"ROLE_IMPORTER");
        call(400,"PATCH","/worker/books/id/900001",Map.of("creator_id",UUID.randomUUID()),"ROLE_IMPORTER");
    }
    @Test void legacyChapterCanBePublishedBeforeContentWithoutWeakeningModernApi() throws Exception {
        call(201,"POST","/worker/books",book(900002),"ROLE_IMPORTER");
        var input=Map.of("index",1,"name","Legacy chapter","word_count",350,"published",true);
        var created=call(201,"POST","/worker/chapters/id/900002",input,"ROLE_IMPORTER");
        assertThat(created.path("book_id").asLong()).isEqualTo(900002);
        assertThat(created.path("word_count").asLong()).isEqualTo(350);
        assertThat(created.path("published").asBoolean()).isTrue();
        var replay=call(201,"POST","/worker/chapters/id/900002",input,"ROLE_IMPORTER");
        assertThat(replay.path("id").asLong()).isEqualTo(created.path("id").asLong());
        long internal=db.queryForObject("select book_id from legacy_worker_books where external_id=900002",Long.class);
        call(200,"GET","/chapters/id/"+internal+"/1",null,null);
        call(404,"GET","/chapters/content/id/"+internal+"/1",null,null);
        call(400,"POST","/chapters/id/"+internal,Map.of("index",2,"name","Modern chapter","published",true),"ROLE_ADMIN");
        call(200,"DELETE","/chapters/id/"+created.path("id").asLong(),null,"ROLE_ADMIN");
        call(409,"POST","/worker/chapters/id/900002",input,"ROLE_IMPORTER");
    }
    @Test void duplicateReferenceCreationIsStableAndReadsAreRestricted() throws Exception {
        var author=Map.of("name","Legacy shared author","local_name","");
        var first=call(201,"POST","/worker/authors",author,"ROLE_IMPORTER");
        assertThat(call(201,"POST","/worker/authors",author,"ROLE_IMPORTER").path("id")).isEqualTo(first.path("id"));
        for (String kind:List.of("genres","tags","book-statuses")) {
            var payload=new LinkedHashMap<String,Object>(); payload.put("name","Reference"); payload.put("slug","reference-"+kind);
            if (kind.equals("tags")) payload.put("type","Theme");
            var a=call(201,"POST","/worker/"+kind,payload,"ROLE_IMPORTER");
            assertThat(call(201,"POST","/worker/"+kind,payload,"ROLE_IMPORTER").path("id")).isEqualTo(a.path("id"));
            call(401,"GET","/worker/"+kind+"/reference-"+kind,null,null);
            call(403,"GET","/worker/"+kind+"/reference-"+kind,null,"ROLE_USER");
        }
    }
    @Test void missingStatusMappingFailsRatherThanUsingAccidentalDatabaseIds() throws Exception {
        var input=book(900003); input.put("status_id",123);
        call(409,"POST","/worker/books",input,"ROLE_IMPORTER");
        call(404,"GET","/worker/books/id/900003",null,"ROLE_IMPORTER");
        assertThat(db.queryForObject("select count(*) from books where slug='source-900003'",Long.class)).isZero();
    }
    @Test void adminBindingRequiresExplicitTargetAndCannotReplaceDeletedBook() throws Exception {
        var input=book(900004); input.remove("id"); input.remove("chapter_count"); input.remove("word_count"); input.remove("view_count"); input.remove("average_rating");
        input.put("note",""); input.put("poster",Map.of()); input.put("synopsis","Synopsis");
        long status=db.queryForObject("select id from book_statuses where slug='compat-status'",Long.class); input.put("status_id",status);
        var created=call(201,"POST","/books",input,"ROLE_ADMIN"); long internal=created.path("id").asLong();
        var bind=Map.of("book_id",internal,"source_status_id",9);
        call(403,"POST","/admin/catalog/worker/books/900004/bind",bind,"ROLE_IMPORTER");
        assertThat(call(200,"POST","/admin/catalog/worker/books/900004/bind",bind,"ROLE_ADMIN").path("id").asLong()).isEqualTo(900004);
        call(409,"POST","/admin/catalog/worker/books/900004/bind",bind,"ROLE_ADMIN");
        call(200,"DELETE","/books/id/"+internal,null,"ROLE_ADMIN");
        call(409,"GET","/worker/books/id/900004",null,"ROLE_IMPORTER");
        call(409,"POST","/worker/books",book(900004),"ROLE_IMPORTER");
    }
}
