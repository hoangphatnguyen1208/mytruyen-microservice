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
    @Test void publicBookBatchPreservesRankAndHidesDrafts() throws Exception {
        expect(400,"GET","/books/topboxes?kind=-1&limit=10",null,null);
        expect(400,"GET","/books/topboxes?kind=1&limit=101",null,null);
        String admin=token("ROLE_ADMIN");
        long a=chapterBook(unique(),true),b=chapterBook(unique(),true),draft=chapterBook(unique(),false);
        long deleted=chapterBook(unique(),true);
        expect(200,"DELETE","/books/id/"+deleted,null,admin);
        var response=expect(200,"GET","/books/batch?ids="+b+"&ids="+draft+"&ids="+a+"&ids="+b+"&ids="+deleted+"&ids=999999",null,null);
        assertThat(ids(response)).containsExactly(b,a);
        expect(400,"GET","/books/batch?ids=0",null,null);
        expect(400,"GET","/books/batch?ids=invalid",null,null);
        expect(400,"GET","/books/batch?"+String.join("&",Collections.nCopies(101,"ids=1")),null,null);
    }
    List<Long> ids(JsonNode response) {
        var result=new ArrayList<Long>(); response.path("data").forEach(row->result.add(row.path("id").asLong())); return result;
    }
    @Test void legacyBookSortsUseDatabaseCountersAndStablePages() throws Exception {
        String admin=token("ROLE_ADMIN"); long status=taxon("book-statuses",unique());
        var created=new ArrayList<Long>();
        for (String name:List.of("Alpha","Beta","Gamma")) {
            var input=book(status,unique()); input.put("name",name); input.put("published",true);
            created.add(expect(201,"POST","/books",input,admin).path("data").path("id").asLong());
        }
        long a=created.get(0),b=created.get(1),c=created.get(2);
        jdbc.update("update book_content_stats set chapter_count=2,word_count=20,latest_chapter_index=4,new_chap_at=TIMESTAMP WITH TIME ZONE '2020-01-01 00:00:00+00' where book_id=?",a);
        jdbc.update("update book_content_stats set chapter_count=1,word_count=10,latest_chapter_index=2,new_chap_at=TIMESTAMP WITH TIME ZONE '2021-01-01 00:00:00+00' where book_id=?",b);
        jdbc.update("delete from book_content_stats where book_id=?",c);
        jdbc.update("update book_engagement_projection set view_count=10,review_count=2,rating_sum=9,comment_count=1,bookmark_count=1 where book_id=?",a);
        jdbc.update("update book_engagement_projection set view_count=20,review_count=4,rating_sum=12,comment_count=2,bookmark_count=2 where book_id=?",b);
        jdbc.update("delete from book_engagement_projection where book_id=?",c);
        expect(201,"POST","/books",book(status,unique()),admin); // Draft must not affect totals or ordering.
        String path="/books?status="+status+"&sort=";
        assertThat(ids(expect(200,"GET",path+"name",null,null))).containsExactly(a,b,c);
        assertThat(ids(expect(200,"GET",path+"-name",null,null))).containsExactly(c,b,a);
        for(String field:List.of("word_count","chapter_count"))
            assertThat(ids(expect(200,"GET",path+"-"+field,null,null))).containsExactly(a,b,c);
        for(String field:List.of("view_count","review_count","comment_count","bookmark_count"))
            assertThat(ids(expect(200,"GET",path+field,null,null))).containsExactly(c,a,b);
        assertThat(ids(expect(200,"GET",path+"-average_rating",null,null))).containsExactly(a,b,c);
        assertThat(ids(expect(200,"GET",path+"average_rating",null,null))).containsExactly(c,b,a);
        assertThat(ids(expect(200,"GET",path+"new_chap_at",null,null))).containsExactly(a,b,c);
        assertThat(ids(expect(200,"GET",path+"-new_chap_at",null,null))).containsExactly(b,a,c);
        assertThat(ids(expect(200,"GET",path+"latest_chapter",null,null))).containsExactly(b,a,c);
        var page=expect(200,"GET",path+"-word_count&limit=1&page=2",null,null);
        assertThat(ids(page)).containsExactly(b);
        assertThat(page.path("pagination").path("total_items").asLong()).isEqualTo(3);
        assertThat(ids(expect(200,"GET",path+"kind",null,null))).containsExactly(a,b,c);
        for(String field:List.of("id","slug","sex","status_id","chapter_per_week","published","created_at","updated_at","published_at"))
            expect(200,"GET",path+"-"+field,null,null);
        expect(400,"GET",path+"author.name",null,null);
        expect(400,"GET",path+"--name",null,null);
    }
    @Test void legacyChapterSortingAndSlugPagination() throws Exception {
        String admin=token("ROLE_ADMIN"),slug=unique(); long b=chapterBook(slug,true),a=chapter(b,1),z=chapter(b,2);
        expect(201,"POST","/chapters/content/id/"+b+"/1",Map.of("content","One two three"),admin);
        expect(201,"POST","/chapters/content/id/"+b+"/2",Map.of("content","One"),admin);
        expect(200,"POST","/chapters/id/"+a+"/publish",null,admin);
        expect(200,"POST","/chapters/id/"+z+"/publish",null,admin);
        String path="/chapters/id/"+b+"?sort=";
        assertThat(ids(expect(200,"GET",path+"word_count",null,null))).containsExactly(z,a);
        assertThat(ids(expect(200,"GET",path+"-word_count",null,null))).containsExactly(a,z);
        assertThat(ids(expect(200,"GET",path+"name",null,null))).containsExactly(a,z);
        assertThat(ids(expect(200,"GET",path+"-name",null,null))).containsExactly(z,a);
        for(String field:List.of("id","book_id","index","created_at","updated_at","published_at","published"))
            expect(200,"GET",path+"-"+field,null,null);
        assertThat(expect(200,"GET","/chapters/slug/"+slug,null,null).path("pagination").path("size").asInt()).isEqualTo(10);
        assertThat(expect(200,"GET","/chapters/id/"+b,null,null).path("pagination").path("size").asInt()).isEqualTo(30);
        var all=expect(200,"GET","/chapters?limit=100",null,null).path("data");
        long previousBook=-1; int previousIndex=-1;
        for(var row:all) {
            long bookId=row.path("book_id").asLong(); int index=row.path("index").asInt();
            assertThat(bookId).isGreaterThanOrEqualTo(previousBook);
            if(bookId==previousBook) assertThat(index).isGreaterThanOrEqualTo(previousIndex);
            previousBook=bookId; previousIndex=index;
        }
        expect(400,"GET",path+"content",null,null);
    }
    @Test void legacyPublishedPatchKeepsValidationAndAtomicStatistics() throws Exception {
        String admin=token("ROLE_ADMIN"); long b=chapterBook(unique(),true),id=chapter(b,1);
        String path="/chapters/id/"+id;
        long before=events(id);
        expect(409,"PATCH",path,Map.of("published",true,"name","Must roll back"),admin);
        assertThat(events(id)).isEqualTo(before);
        assertThat(expect(200,"GET","/admin/catalog/chapters/id/"+b+"/1",null,admin).path("data").path("name").asText()).isEqualTo("Chapter 1");
        expect(201,"POST","/chapters/content/id/"+b+"/1",Map.of("content","Two words"),admin);
        expect(403,"PATCH",path,Map.of("published",true),token("ROLE_USER"));
        expect(200,"PATCH",path,Map.of("published",true,"name","Published via legacy PATCH"),admin);
        counts(b,1,2,1);
        expect(200,"GET","/chapters/id/"+b+"/1",null,null);
        expect(400,"PATCH",path,Collections.singletonMap("published",null),admin);
        expect(200,"PATCH",path,Map.of("published",false,"index",3),admin);
        counts(b,0,0,null);
        expect(404,"GET","/chapters/id/"+b+"/3",null,null);
        assertThat(jdbc.queryForObject("select event_type from catalog_outbox where aggregate_id=? order by aggregate_version desc limit 1",String.class,id)).isEqualTo("ChapterUnpublished");
    }
    long events(long id) { return jdbc.queryForObject("select count(*) from catalog_outbox where aggregate_id=?",Long.class,id); }
    JsonNode counters(long bookId) throws Exception {
        return expect(200,"GET","/admin/catalog/books/id/"+bookId,null,token("ROLE_ADMIN")).path("data");
    }
    void counts(long bookId,long chapters,long words,Integer latest) throws Exception {
        var data=counters(bookId);
        assertThat(data.path("chapter_count").asLong()).isEqualTo(chapters);
        assertThat(data.path("word_count").asLong()).isEqualTo(words);
        if (latest==null) assertThat(data.path("latest_chapter").isNull()).isTrue();
        else assertThat(data.path("latest_chapter").asInt()).isEqualTo(latest);
    }
    @Test void publicationMaintainsStatsAndIdempotentEvents() throws Exception {
        String admin=token("ROLE_ADMIN"); long b=chapterBook(unique(),true);
        long one=chapter(b,1),two=chapter(b,2);
        expect(409,"POST","/chapters/id/"+one+"/publish",null,admin);
        assertThat(events(one)).isEqualTo(1);
        expect(201,"POST","/chapters/content/id/"+b+"/1",Map.of("content","One two"),admin);
        expect(201,"POST","/chapters/content/id/"+b+"/2",Map.of("content","Three four five"),admin);
        counts(b,0,0,null);
        expect(401,"POST","/chapters/id/"+one+"/publish",null,null);
        expect(403,"POST","/chapters/id/"+one+"/publish",null,token("ROLE_USER"));
        var first=expect(200,"POST","/chapters/id/"+one+"/publish",null,admin).path("data");
        long before=events(one);
        var again=expect(200,"POST","/chapters/id/"+one+"/publish",null,admin).path("data");
        assertThat(again.path("version").asLong()).isEqualTo(first.path("version").asLong());
        assertThat(again.path("published_at").asText()).isEqualTo(first.path("published_at").asText());
        assertThat(events(one)).isEqualTo(before); counts(b,1,2,1);
        expect(200,"POST","/chapters/id/"+two+"/publish",null,admin); counts(b,2,5,2);
        expect(200,"PATCH","/chapters/content/id/"+b+"/2",Map.of("content","Changed"),admin); counts(b,2,3,2);
        expect(200,"PATCH","/chapters/id/"+two,Map.of("index",4),admin); counts(b,2,3,4);
        long failuresBefore=events(two);
        expect(409,"PATCH","/chapters/id/"+two,Map.of("index",1),admin);
        assertThat(events(two)).isEqualTo(failuresBefore); counts(b,2,3,4);
        expect(200,"POST","/chapters/id/"+two+"/unpublish",null,admin); counts(b,1,2,1);
        long unpublishedEvents=events(two);
        expect(200,"POST","/chapters/id/"+two+"/unpublish",null,admin);
        assertThat(events(two)).isEqualTo(unpublishedEvents);
        expect(404,"GET","/chapters/id/"+b+"/4",null,null);
        expect(200,"POST","/chapters/id/"+two+"/publish",null,admin); counts(b,2,3,4);
        expect(200,"DELETE","/chapters/id/"+two,null,admin); counts(b,1,2,1);
        expect(200,"POST","/chapters/id/"+one+"/unpublish",null,admin); counts(b,0,0,null);
        assertThat(counters(b).path("new_chap_at").isNull()).isTrue();
        expect(200,"DELETE","/chapters/content/id/"+b+"/1",null,admin);
        expect(409,"POST","/chapters/id/"+one+"/publish",null,admin);
        var rows=jdbc.queryForList("select aggregate_version,payload,published_at,correlation_id from catalog_outbox where aggregate_id=? order by aggregate_version",one);
        long previous=-1;
        for(var row:rows) {
            long version=((Number)row.get("AGGREGATE_VERSION")).longValue();
            assertThat(version).isGreaterThan(previous); previous=version;
            assertThat(row.get("PUBLISHED_AT")).isNull();
            assertThat(row.get("CORRELATION_ID")).isNotNull();
            assertThat(mapper.readTree(row.get("PAYLOAD").toString()).path("book_id").asLong()).isEqualTo(b);
        }
    }
    @Test void concurrentPublicationsKeepCombinedStatistics() throws Exception {
        String admin=token("ROLE_ADMIN"); long b=chapterBook(unique(),true),one=chapter(b,1),two=chapter(b,2);
        expect(201,"POST","/chapters/content/id/"+b+"/1",Map.of("content","One two"),admin);
        expect(201,"POST","/chapters/content/id/"+b+"/2",Map.of("content","Three"),admin);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await();return request("POST","/chapters/id/"+one+"/publish",null,admin).statusCode();});
            var z=pool.submit(()->{start.await();return request("POST","/chapters/id/"+two+"/publish",null,admin).statusCode();});
            start.countDown();
            assertThat(a.get(30,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(z.get(30,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        } finally { pool.shutdownNow(); }
        counts(b,2,3,2);
    }
    @org.springframework.beans.factory.annotation.Autowired online.mytruyen.catalog.service.ChapterService chapterService;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Test void rolledBackPublicationLeavesNoStatsOrOutboxChange() throws Exception {
        String admin=token("ROLE_ADMIN"); long b=chapterBook(unique(),true),id=chapter(b,1);
        expect(201,"POST","/chapters/content/id/"+b+"/1",Map.of("content","Rollback text"),admin);
        long before=events(id);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->{
            chapterService.publish(id); tx.setRollbackOnly();
        });
        counts(b,0,0,null); assertThat(events(id)).isEqualTo(before);
        assertThat(expect(200,"GET","/admin/catalog/chapters/id/"+b+"/1",null,admin).path("data").path("published").asBoolean()).isFalse();
    }
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
        expect(200,"POST","/chapters/id/"+id+"/publish",null,admin);
        expect(200,"GET","/chapters/id/"+bookId+"/1",null,null);
        expect(200,"GET","/chapters/slug/"+slug+"/1",null,null);
        expect(200,"GET",content,null,null);
        expect(200,"GET","/chapters/content/slug/"+slug+"/1",null,null);
        assertThat(expect(200,"GET","/chapters/id/"+bookId,null,null).path("pagination").path("total_items").asLong()).isEqualTo(1);
        expect(200,"PATCH","/chapters/id/"+id,Map.of("name","Published edit"),admin);
        expect(200,"PATCH",content,Map.of("content","Updated public content"),admin);
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
