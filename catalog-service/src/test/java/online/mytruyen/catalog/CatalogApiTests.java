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
