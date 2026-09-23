package online.mytruyen.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:catalog-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=",
    "management.health.rabbit.enabled=false"
})
class CatalogApiTests extends CatalogJwtTestSupport {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @LocalServerPort int port;
    final HttpClient client=HttpClient.newHttpClient();
    final ObjectMapper mapper=new ObjectMapper();
    HttpResponse<String> request(String method,String path,Object body,String jwt) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path))
            .header("Content-Type","application/json");
        if (jwt!=null) builder.header("Authorization","Bearer "+jwt);
        builder.method(method,body==null ? HttpRequest.BodyPublishers.noBody() :
            HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode expect(int status,String method,String path,Object body,String jwt) throws Exception {
        var response=request(method,path,body,jwt);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return mapper.readTree(response.body());
    }
    String unique() { return "test-"+UUID.randomUUID(); }
    long chapterBook(String slug, boolean published) throws Exception {
        var input=book(taxon("book-statuses",unique()),slug); input.put("published",published);
        return expect(201,"POST","/books",input,token("ROLE_ADMIN")).path("data").path("id").asLong();
    }
    long chapter(long bookId,int index) throws Exception {
        return expect(201,"POST","/chapters/id/"+bookId,Map.of("index",index,"name","Chapter "+index),token("ROLE_ADMIN"))
            .path("data").path("id").asLong();
    }
    @Test void chapterDraftCrudAndContentLifecycle() throws Exception {
        String admin=token("ROLE_ADMIN"),slug=unique(); long bookId=chapterBook(slug,true);
        long id=chapter(bookId,1);
        String path="/chapters/content/id/"+bookId+"/1";
        var draft=expect(200,"GET","/admin/catalog/chapters/id/"+bookId+"/1",null,admin).path("data");
        assertThat(draft.path("creator_id").asText()).isEqualTo(ADMIN_ID.toString());
        assertThat(draft.path("published").asBoolean()).isFalse();
        expect(404,"GET","/chapters/id/"+bookId+"/1",null,admin);
        String text="Xin chào\n thế\u00a0giới";
        var content=expect(201,"POST",path,Map.of("content",text),admin).path("data");
        assertThat(content.path("chapter_id").asLong()).isEqualTo(id);
        assertThat(content.path("content_hash").asText()).isEqualTo(java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        var counted=expect(200,"GET","/admin/catalog/chapters/id/"+bookId+"/1",null,admin).path("data");
        assertThat(counted.path("word_count").asLong()).isEqualTo(4);
        assertThat(counted.path("version").asLong()).isGreaterThan(draft.path("version").asLong());
        expect(409,"POST",path,Map.of("content","Duplicate"),admin);
        expect(404,"GET",path,null,null);
        expect(200,"PATCH",path,Map.of("content","Two words"),admin);
        expect(200,"PATCH","/chapters/id/"+id,Map.of("index",2,"name","Renamed"),admin);
        expect(404,"GET","/admin/catalog"+path,null,admin);
        var renamed=expect(200,"GET","/admin/catalog/chapters/slug/"+slug+"/2",null,admin).path("data");
        assertThat(renamed.path("word_count").asLong()).isEqualTo(2);
        expect(200,"DELETE","/chapters/content/slug/"+slug+"/2",null,admin);
        expect(404,"GET","/admin/catalog/chapters/content/id/"+bookId+"/2",null,admin);
        assertThat(expect(200,"GET","/admin/catalog/chapters/id/"+bookId+"/2",null,admin).path("data").path("word_count").asLong()).isZero();
        expect(201,"POST","/chapters/content/id/"+bookId+"/2",Map.of("content","Recreated"),admin);
        expect(200,"DELETE","/chapters/id/"+id,null,admin);
        expect(404,"GET","/admin/catalog/chapters/id/"+bookId+"/2",null,admin);
        expect(404,"GET","/admin/catalog/chapters/content/id/"+bookId+"/2",null,admin);
        expect(409,"POST","/chapters/id/"+bookId,Map.of("index",2,"name","Reserved index"),admin);
        assertThat(expect(200,"GET","/books/id/"+bookId,null,null).path("data").path("chapter_count").asLong()).isZero();
    }
    @Test void chapterValidationPermissionsAndRollback() throws Exception {
        String admin=token("ROLE_ADMIN"); long bookId=chapterBook(unique(),false);
        String path="/chapters/id/"+bookId;
        expect(401,"POST",path,Map.of("index",1,"name","A"),null);
        expect(403,"POST",path,Map.of("index",1,"name","A"),token("ROLE_USER"));
        expect(403,"GET","/admin/catalog/chapters",null,token("ROLE_USER"));
        expect(401,"GET","/admin/catalog/chapters",null,null);
        expect(400,"POST",path,Map.of("index",0,"name","A"),admin);
        expect(400,"POST",path,Map.of("index",1,"name","A","published",true),admin);
        expect(400,"POST",path,Map.of("index",1,"name","A","creator_id",ADMIN_ID),admin);
        long id=chapter(bookId,1); chapter(bookId,2);
        expect(409,"PATCH","/chapters/id/"+id,Map.of("index",2,"name","Must rollback"),admin);
        assertThat(expect(200,"GET","/admin/catalog/chapters/id/"+bookId+"/1",null,admin).path("data").path("name").asText()).isEqualTo("Chapter 1");
        for(String field:List.of("word_count","book_id","creator_id","version"))
            expect(400,"PATCH","/chapters/id/"+id,Map.of(field,10),admin);
        String content="/chapters/content/id/"+bookId+"/1";
        expect(404,"PATCH",content,Map.of("content","Missing"),admin);
        expect(400,"POST",content,Map.of("content"," "),admin);
        expect(400,"POST",content,Map.of("content","Valid","chapter_id",id),admin);
        expect(201,"POST",content,Map.of("content","Keep me"),admin);
        expect(400,"PATCH",content,Collections.singletonMap("content",null),admin);
        expect(400,"PATCH",content,Map.of("content_hash","forged"),admin);
        expect(403,"DELETE",content,null,token("ROLE_USER"));
        expect(404,"GET",path,null,null);
        expect(200,"DELETE","/books/id/"+bookId,null,admin);
        expect(404,"PATCH","/chapters/id/"+id,Map.of("name","Hidden"),admin);
        expect(404,"POST",content,Map.of("content","Hidden"),admin);
    }
    @Test void publishedChapterVisibilityAndWriteGuard() throws Exception {
        String admin=token("ROLE_ADMIN"),slug=unique(); long bookId=chapterBook(slug,true),id=chapter(bookId,1);
        String content="/chapters/content/id/"+bookId+"/1";
        expect(201,"POST",content,Map.of("content","Public text"),admin);
        // Fixture only: HTTP publication will be implemented in the next stage.
        jdbc.update("update chapters set published=true,published_at=CURRENT_TIMESTAMP where id=?",id);
        expect(200,"GET","/chapters/id/"+bookId+"/1",null,null);
        expect(200,"GET","/chapters/slug/"+slug+"/1",null,null);
        expect(200,"GET",content,null,null);
        expect(200,"GET","/chapters/content/slug/"+slug+"/1",null,null);
        assertThat(expect(200,"GET","/chapters/id/"+bookId,null,null).path("pagination").path("total_items").asLong()).isEqualTo(1);
        expect(409,"PATCH","/chapters/id/"+id,Map.of("name","Blocked"),admin);
        expect(409,"DELETE","/chapters/id/"+id,null,admin);
        expect(409,"PATCH",content,Map.of("content","Blocked"),admin);
        expect(409,"DELETE",content,null,admin);
        expect(200,"PATCH","/books/id/"+bookId,Map.of("published",false),admin);
        expect(404,"GET",content,null,null);
        expect(404,"GET","/chapters/slug/"+slug+"/1",null,null);
        expect(200,"GET","/admin/catalog/chapters/content/slug/"+slug+"/1",null,admin);
        expect(200,"DELETE","/books/id/"+bookId,null,admin);
        expect(404,"GET","/admin/catalog/chapters/content/id/"+bookId+"/1",null,admin);
    }
    @Test void chapterPaginationAndSlugCreation() throws Exception {
        String admin=token("ROLE_ADMIN"),slug=unique(); long bookId=chapterBook(slug,true);
        for(int index:List.of(3,1,2)) expect(201,"POST","/chapters/slug/"+slug,Map.of("index",index,"name","C"+index),admin);
        var page=expect(200,"GET","/admin/catalog/chapters/slug/"+slug+"?limit=2",null,admin);
        assertThat(page.path("pagination").path("total_items").asLong()).isEqualTo(3);
        assertThat(page.path("data").get(0).path("index").asInt()).isEqualTo(1);
        assertThat(page.path("data").get(0).has("content")).isFalse();
        assertThat(expect(200,"GET","/admin/catalog/chapters/id/"+bookId+"?limit=2&page=2",null,admin).path("data").get(0).path("index").asInt()).isEqualTo(3);
        assertThat(expect(200,"GET","/chapters/id/"+bookId,null,null).path("data").size()).isZero();
        expect(200,"GET","/chapters",null,null);
        expect(400,"GET","/chapters?limit=101",null,null);
        expect(400,"GET","/chapters?sort=invalid",null,null);
        expect(400,"GET","/chapters/id/not-a-number",null,null);
        expect(404,"GET","/chapters/id/999999",null,null);
    }
    @Test void concurrentChapterCreationDoesNotDuplicateIndex() throws Exception {
        long bookId=chapterBook(unique(),true); String admin=token("ROLE_ADMIN");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> create=()-> { start.await(); return request("POST","/chapters/id/"+bookId,Map.of("index",1,"name","Concurrent"),admin).statusCode(); };
            var first=pool.submit(create); var second=pool.submit(create); start.countDown();
            assertThat(List.of(first.get(30,java.util.concurrent.TimeUnit.SECONDS),second.get(30,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally { pool.shutdownNow(); }
        assertThat(expect(200,"GET","/admin/catalog/chapters/id/"+bookId,null,admin).path("pagination").path("total_items").asLong()).isEqualTo(1);
    }
    long taxon(String kind,String slug) throws Exception {
        return expect(201,"POST","/"+kind,Map.of("name",slug,"slug",slug),token("ROLE_ADMIN")).path("data").path("id").asLong();
    }
    Map<String,Object> book(long status,String slug) {
        return new LinkedHashMap<>(Map.of("name","Book","slug",slug,"status_id",status,"kind",1,"sex",0,"synopsis","Summary"));
    }
    @Test void permissionsAndMalformedTokens() throws Exception {
        expect(200,"GET","/books",null,null);
        expect(401,"POST","/authors",Map.of("name","A"),null);
        expect(403,"POST","/authors",Map.of("name","A"),token("ROLE_USER"));
        expect(401,"GET","/books",null,"bad-token");
        expect(401,"GET","/admin/catalog/books",null,null);
        expect(403,"GET","/admin/catalog/books",null,token("ROLE_USER"));
    }
    @Test void rejectsInvalidJwtClaimsAndForgedHeaders() throws Exception {
        for (String variant : List.of("issuer","audience","expired","no-expiry","algorithm","signature")) {
            var jwt=io.jsonwebtoken.Jwts.builder().setSubject(ADMIN_ID.toString())
                .setIssuer(variant.equals("issuer") ? "other" : "mytruyen-auth")
                .setAudience(variant.equals("audience") ? "other" : "mytruyen-api")
                .setIssuedAt(new Date(System.currentTimeMillis()-10000)).claim("roles",List.of("ROLE_ADMIN"));
            if (!variant.equals("no-expiry")) jwt.setExpiration(new Date(System.currentTimeMillis()+(variant.equals("expired") ? -1000 : 300000)));
            var key=KEYS.getPrivate();
            if (variant.equals("signature")) {
                var generator=java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
                key=generator.generateKeyPair().getPrivate();
            }
            String signed=jwt.signWith(key,variant.equals("algorithm") ? io.jsonwebtoken.SignatureAlgorithm.RS512 : io.jsonwebtoken.SignatureAlgorithm.RS256).compact();
            expect(401,"GET","/admin/catalog/books",null,signed);
        }
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/admin/catalog/books"))
            .header("X-User-Roles","ROLE_ADMIN").header("X-User-Id",ADMIN_ID.toString()).GET().build();
        assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }
    @Test void tagAndTaxonomyUpdates() throws Exception {
        String admin=token("ROLE_ADMIN"),slug=unique();
        expect(400,"POST","/tags",Map.of("name","Tag","slug",slug),admin);
        expect(201,"POST","/tags",Map.of("name","Tag","slug",slug,"type","theme"),admin);
        expect(200,"PATCH","/tags/"+slug,Map.of("description","Description"),admin);
        assertThat(expect(200,"GET","/tags/"+slug,null,null).path("data").path("description").asText()).isEqualTo("Description");
        expect(400,"PATCH","/tags/"+slug,Map.of("id",1),admin);
        expect(200,"DELETE","/tags/"+slug,null,admin);
        expect(404,"GET","/tags/"+slug,null,null);
    }
    @Test void bookLifecycleHidesDraftsAndDeletedBooks() throws Exception {
        String admin=token("ROLE_ADMIN"), slug=unique();
        long status=taxon("book-statuses",unique());
        var created=expect(201,"POST","/books",book(status,slug),admin).path("data");
        long id=created.path("id").asLong();
        assertThat(created.path("creator_id").asText()).isEqualTo(ADMIN_ID.toString());
        assertThat(created.path("chapter_count").asLong()).isZero();
        expect(404,"GET","/books/id/"+id,null,null);
        expect(404,"GET","/books/slug/"+slug,null,admin);
        expect(200,"GET","/admin/catalog/books/id/"+id,null,admin);
        expect(200,"PATCH","/books/id/"+id,Map.of("published",true),admin);
        var visible=expect(200,"GET","/books/slug/"+slug,null,null).path("data");
        assertThat(visible.path("published_at").isNull()).isFalse();
        var list=expect(200,"GET","/books?status="+status,null,null);
        assertThat(list.path("pagination").path("total_items").asLong()).isEqualTo(1);
        expect(200,"PATCH","/books/slug/"+slug,Map.of("published",false),admin);
        expect(404,"GET","/books/id/"+id,null,null);
        expect(200,"DELETE","/books/id/"+id,null,admin);
        expect(404,"GET","/admin/catalog/books/id/"+id,null,admin);
        expect(404,"PATCH","/books/id/"+id,Map.of("published",true),admin);
    }
    @Test void validatesReferencesAndReadOnlyFields() throws Exception {
        String admin=token("ROLE_ADMIN"),slug=unique();
        long status=taxon("book-statuses",unique());
        var input=book(status,slug);
        input.put("creator_id",UUID.randomUUID());
        expect(400,"POST","/books",input,admin);
        input.remove("creator_id"); input.put("genre_ids",List.of(999999));
        expect(404,"POST","/books",input,admin);
        input.remove("genre_ids");
        long id=expect(201,"POST","/books",input,admin).path("data").path("id").asLong();
        expect(409,"POST","/books",input,admin);
        expect(400,"PATCH","/books/id/"+id,Map.of("view_count",100),admin);
        expect(400,"PATCH","/books/id/"+id,Map.of("name"," "),admin);
        expect(404,"PATCH","/books/id/"+id,Map.of("status_id",999999),admin);
        assertThat(expect(200,"GET","/admin/catalog/books/id/"+id,null,admin).path("data").path("status_id").asLong()).isEqualTo(status);
        expect(400,"GET","/books?page=0",null,null);
        expect(400,"GET","/books?limit=101",null,null);
        expect(400,"GET","/books?sort=sql",null,null);
    }
    @Test void taxonomyRelationsAndNullablePatch() throws Exception {
        String admin=token("ROLE_ADMIN"),statusSlug=unique(), genreSlug=unique();
        long status=taxon("book-statuses",statusSlug), genre=taxon("genres",genreSlug);
        String name=unique();
        String author=expect(201,"POST","/authors",Map.of("name",name),admin).path("data").path("id").asText();
        var input=book(status,unique()); input.put("author_id",author); input.put("genre_ids",List.of(genre));
        long id=expect(201,"POST","/books",input,admin).path("data").path("id").asLong();
        expect(409,"DELETE","/genres/delete/"+genre,null,admin);
        expect(409,"DELETE","/book-statuses/"+statusSlug,null,admin);
        expect(409,"DELETE","/authors/"+author,null,admin);
        var patch=new HashMap<String,Object>(); patch.put("author_id",null); patch.put("genre_ids",List.of());
        var data=expect(200,"PATCH","/books/id/"+id,patch,admin).path("data");
        assertThat(data.path("author").isNull()).isTrue();
        assertThat(data.path("genres").size()).isZero();
        expect(200,"DELETE","/genres/delete/"+genre,null,admin);
        expect(200,"DELETE","/authors/"+author,null,admin);
        expect(201,"POST","/authors",Map.of("name",name),admin);
        expect(201,"POST","/authors",Map.of("name",name),admin);
        expect(409,"GET","/authors/"+name,null,null);
    }
}
