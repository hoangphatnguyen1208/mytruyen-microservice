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
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:book-import;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.username=sa","spring.datasource.password=", "management.health.rabbit.enabled=false"
})
class BookImportTests extends CatalogJwtTestSupport {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    final HttpClient http=HttpClient.newHttpClient();
    final ObjectMapper json=new ObjectMapper();
    String unique() { return UUID.randomUUID().toString(); }
    String path(String id) { return "/internal/import/books/metruyencv/"+id; }
    JsonNode call(int status,String method,String path,Object body,String role) throws Exception {
        var r=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path))
            .timeout(java.time.Duration.ofSeconds(20)).header("Content-Type","application/json");
        if (role!=null) r.header("Authorization","Bearer "+token(role));
        r.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=http.send(r.build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
    Map<String,Object> metadata() throws Exception {
        long status=call(201,"POST","/book-statuses",Map.of("name","Ongoing","slug",unique()),"ROLE_ADMIN").path("id").asLong();
        var m=new LinkedHashMap<String,Object>();
        m.put("name","Imported story"); m.put("slug",unique()); m.put("status_id",status);
        m.put("kind",1); m.put("sex",1); m.put("synopsis","Synopsis");
        return m;
    }
    long events(long book) { return db.queryForObject("select count(*) from search_outbox where book_id=?",Long.class,book); }

    @Test void createsDraftAndReplayHasNoExtraBookOrSearchEvent() throws Exception {
        String external=unique(); var input=Map.of("metadata",metadata());
        var first=call(200,"PUT",path(external),input,"ROLE_IMPORTER");
        long id=first.path("book_id").asLong();
        assertThat(first.path("outcome").asText()).isEqualTo("created");
        var replay=call(200,"PUT",path(external),input,"ROLE_IMPORTER");
        assertThat(replay.path("book_id").asLong()).isEqualTo(id);
        assertThat(replay.path("outcome").asText()).isEqualTo("unchanged");
        assertThat(events(id)).isEqualTo(1);
        assertThat(call(200,"GET",path(external),null,"ROLE_IMPORTER").path("book_id").asLong()).isEqualTo(id);
        call(404,"GET","/books/id/"+id,null,null);
        assertThat(call(200,"GET","/admin/catalog/books/id/"+id,null,"ROLE_ADMIN").path("published").asBoolean()).isFalse();
    }

    @Test void updateRequiresVersionAndReplayIsIdempotent() throws Exception {
        String external=unique(); var m=metadata();
        var first=call(200,"PUT",path(external),Map.of("metadata",m),"ROLE_IMPORTER");
        long id=first.path("book_id").asLong(), version=first.path("version").asLong();
        m.put("name","New source title");
        call(409,"PUT",path(external),Map.of("metadata",m),"ROLE_IMPORTER");
        var update=Map.of("metadata",m,"expected_version",version);
        var changed=call(200,"PUT",path(external),update,"ROLE_IMPORTER");
        assertThat(changed.path("outcome").asText()).isEqualTo("updated");
        assertThat(changed.path("version").asLong()).isGreaterThan(version);
        assertThat(call(200,"PUT",path(external),update,"ROLE_IMPORTER").path("outcome").asText()).isEqualTo("unchanged");
        assertThat(events(id)).isEqualTo(2);
        m.put("name","Stale update");
        call(409,"PUT",path(external),update,"ROLE_IMPORTER");
        assertThat(events(id)).isEqualTo(2);
    }

    @Test void manualEditsAndDeletionAreNeverOverwritten() throws Exception {
        String external=unique(); var input=Map.of("metadata",metadata());
        var first=call(200,"PUT",path(external),input,"ROLE_IMPORTER"); long id=first.path("book_id").asLong();
        call(200,"PATCH","/books/id/"+id,Map.of("name","Edited by admin"),"ROLE_ADMIN");
        assertThat(call(200,"GET",path(external),null,"ROLE_IMPORTER").path("outcome").asText()).isEqualTo("manual_review");
        call(409,"PUT",path(external),input,"ROLE_IMPORTER");
        call(200,"DELETE","/books/id/"+id,null,"ROLE_ADMIN");
        call(409,"GET",path(external),null,"ROLE_IMPORTER");
        call(409,"PUT",path(external),input,"ROLE_IMPORTER");
        assertThat(db.queryForObject("select count(*) from book_import_mappings where external_id=?",Long.class,external)).isEqualTo(1);
    }

    @Test void onlyImporterAndAdminCanUseImportButImporterCannotManageCatalog() throws Exception {
        var input=Map.of("metadata",metadata()); String p=path(unique());
        for (String method:List.of("GET","PUT")) {
            call(401,method,p,method.equals("PUT") ? input : null,null);
            call(403,method,p,method.equals("PUT") ? input : null,"ROLE_USER");
        }
        var first=call(200,"PUT",p,input,"ROLE_ADMIN"); long id=first.path("book_id").asLong();
        call(403,"PATCH","/books/id/"+id,Map.of("name","Forbidden"),"ROLE_IMPORTER");
        call(403,"GET","/admin/catalog/books",null,"ROLE_IMPORTER");
        call(403,"POST","/book-statuses",Map.of("name","Forbidden","slug",unique()),"ROLE_IMPORTER");
    }

    @Test void rejectsReadOnlyFieldsUnknownSourcesAndInvalidReferencesWithoutPartialWrites() throws Exception {
        var m=metadata(); String external=unique();
        for (String field:List.of("id","published","view_count","chapter_count")) {
            m.put(field,1); call(400,"PUT",path(external),Map.of("metadata",m),"ROLE_IMPORTER"); m.remove(field);
        }
        call(404,"PUT","/internal/import/books/unknown/"+external,Map.of("metadata",m),"ROLE_IMPORTER");
        m.put("status_id",Long.MAX_VALUE);
        call(404,"PUT",path(external),Map.of("metadata",m),"ROLE_IMPORTER");
        assertThat(db.queryForObject("select count(*) from book_import_mappings where external_id=?",Long.class,external)).isZero();
        assertThat(db.queryForObject("select count(*) from books where slug=?",Long.class,m.get("slug"))).isZero();
    }

    @Test void slugCollisionDoesNotAutoLinkExistingBook() throws Exception {
        var m=metadata(); String a=unique(),b=unique();
        call(200,"PUT",path(a),Map.of("metadata",m),"ROLE_IMPORTER");
        call(409,"PUT",path(b),Map.of("metadata",m),"ROLE_IMPORTER");
        call(404,"GET",path(b),null,"ROLE_IMPORTER");
        assertThat(db.queryForObject("select count(*) from books where slug=?",Long.class,m.get("slug"))).isEqualTo(1);
    }

    @Test void disabledSourcesAndInvalidIdentifiersAreRejected() throws Exception {
        var input=Map.of("metadata",metadata());
        call(400,"PUT","/internal/import/books/INVALID/123",input,"ROLE_IMPORTER");
        call(400,"PUT",path("bad%20id"),input,"ROLE_IMPORTER");
        db.update("insert into import_sources(code,enabled) values (?,false)","disabled");
        call(409,"PUT","/internal/import/books/disabled/123",input,"ROLE_IMPORTER");
        call(409,"GET","/internal/import/books/disabled/123",null,"ROLE_IMPORTER");
    }

    @Test void simultaneousRetriesCreateOneBook() throws Exception {
        String external=unique(); var input=Map.of("metadata",metadata());
        var pool=Executors.newFixedThreadPool(6); var start=new CountDownLatch(1);
        try {
            var tasks=new ArrayList<Future<Long>>();
            for (int i=0;i<6;i++) tasks.add(pool.submit(()->{
                start.await(); return call(200,"PUT",path(external),input,"ROLE_IMPORTER").path("book_id").asLong();
            }));
            start.countDown(); var ids=new HashSet<Long>();
            for (var task:tasks) ids.add(task.get(30,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1); assertThat(events(ids.iterator().next())).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
