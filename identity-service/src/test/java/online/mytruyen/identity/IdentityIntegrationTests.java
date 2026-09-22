package online.mytruyen.identity;

import online.mytruyen.identity.domain.UserEntity;
import online.mytruyen.identity.dto.Contracts;
import online.mytruyen.identity.security.JwtService;
import online.mytruyen.identity.service.AccountService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityIntegrationTests {
    static final KeyPair KEYS=keys();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->"jdbc:h2:mem:identity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        p.add("spring.datasource.username",()->"sa");
        p.add("spring.datasource.password",()->"");
        p.add("jwt.private-key",()->Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded()));
        p.add("jwt.public-key",()->Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        p.add("identity.bootstrap.enabled",()->false);
    }
    static KeyPair keys() {
        try { var g=KeyPairGenerator.getInstance("RSA"); g.initialize(2048); return g.generateKeyPair(); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired AccountService accounts;
    @Autowired JwtService jwt;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    final ObjectMapper json=new ObjectMapper();
    final HttpClient http=HttpClient.newHttpClient();
    @BeforeEach void clean() {
        for(String table:List.of("outbox_events","refresh_tokens","auth_sessions","user_roles","user_credentials","users"))
            db.update("DELETE FROM "+table);
    }
    record Result(int status,JsonNode body) {}
    Result request(String method,String path,Object body,String token) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        if(token!=null) b.header("Authorization","Bearer "+token);
        b.header("Content-Type","application/json");
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        return new Result(r.statusCode(),r.body().isBlank()?json.nullNode():json.readTree(r.body()));
    }
    Result post(String path,Object body) throws Exception { return request("POST","/api/v1/auth/"+path,body,null); }
    JsonNode registerLogin(String email) throws Exception {
        assertThat(post("register",Map.of("email",email,"password","Password123!")).status()).isEqualTo(201);
        var r=post("login",Map.of("email",email,"password","Password123!"));
        assertThat(r.status()).isEqualTo(200); return r.body().get("data");
    }
    String access(JsonNode t) { return t.get("access_token").asText(); }
    String refresh(JsonNode t) { return t.get("refresh_token").asText(); }

    @Test void registerLoginAndProfileDoNotExposeCredentials() throws Exception {
        var t=registerLogin("Reader@example.com");
        var me=request("GET","/api/v1/users/me",null,access(t));
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().at("/data/email").asText()).isEqualTo("reader@example.com");
        assertThat(me.body().toString()).doesNotContain("password","token_hash");
        assertThat(jwt.verify(access(t)).get("roles",List.class)).containsExactly("ROLE_USER");
        assertThat(db.queryForObject("SELECT token_hash FROM refresh_tokens",String.class)).isNotEqualTo(refresh(t));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_events",Integer.class)).isEqualTo(1);
        assertThat(request("PATCH","/api/v1/users/me",Map.of("full_name","Reader"),access(t)).status()).isEqualTo(200);
    }
    @Test void duplicatesValidationAndRegistrationCannotEscalate() throws Exception {
        var t=registerLogin("reader@example.com");
        assertThat(post("register",Map.of("email","READER@example.com","password","Password123!")).status()).isEqualTo(409);
        assertThat(post("register",Map.of("email","bad","password","short")).status()).isEqualTo(400);
        var r=post("register",Map.of("email","second@example.com","password","Password123!","roles",List.of(2)));
        assertThat(r.status()).isIn(201,400);
        if(r.status()==201) assertThat(r.body().at("/data/roles").toString()).doesNotContain("ADMIN");
        assertThat(request("GET","/api/v1/users",null,access(t)).status()).isEqualTo(403);
        assertThat(request("GET","/api/v1/users/me",null,null).status()).isEqualTo(401);
    }
    @Test void refreshRotationReuseCommitsFamilyRevocation() throws Exception {
        var t=registerLogin("reader@example.com");
        var next=post("refresh-token",Map.of("refresh_token",refresh(t)));
        assertThat(next.status()).isEqualTo(200);
        assertThat(post("refresh-token",Map.of("refresh_token",refresh(t))).status()).isEqualTo(401);
        assertThat(post("refresh-token",Map.of("refresh_token",next.body().at("/data/refresh_token").asText())).status()).isEqualTo(401);
        assertThat(request("GET","/api/v1/users/me",null,next.body().at("/data/access_token").asText()).status()).isEqualTo(401);
    }
    @Test void logoutRevokesSessionAndIsIdempotent() throws Exception {
        var t=registerLogin("reader@example.com");
        for(int i=0;i<2;i++) assertThat(post("logout",Map.of("refresh_token",refresh(t))).status()).isEqualTo(200);
        assertThat(post("refresh-token",Map.of("refresh_token",refresh(t))).status()).isEqualTo(401);
        assertThat(request("GET","/api/v1/users/me",null,access(t)).status()).isEqualTo(401);
    }
    @Test void passwordChangeRevokesAllSessions() throws Exception {
        var t=registerLogin("reader@example.com");
        var other=post("login",Map.of("email","reader@example.com","password","Password123!")).body().get("data");
        assertThat(request("POST","/api/v1/users/me/password",Map.of("current_password","wrong","new_password","NewPassword123!"),access(t)).status()).isEqualTo(401);
        assertThat(request("POST","/api/v1/users/me/password",Map.of("current_password","Password123!","new_password","NewPassword123!"),access(t)).status()).isEqualTo(200);
        assertThat(request("GET","/api/v1/users/me",null,access(other)).status()).isEqualTo(401);
        assertThat(post("login",Map.of("email","reader@example.com","password","Password123!")).status()).isEqualTo(401);
        assertThat(post("login",Map.of("email","reader@example.com","password","NewPassword123!")).status()).isEqualTo(200);
    }
    @Test void adminDisableAndLastAdminProtection() throws Exception {
        var admin=accounts.adminCreate(new Contracts.AdminCreate("admin@example.com","admin","Password123!",List.of(1,2)));
        String a=access(post("login",Map.of("email",admin.email(),"password","Password123!")).body().get("data"));
        var t=registerLogin("reader@example.com");
        String id=request("GET","/api/v1/users/me",null,access(t)).body().at("/data/id").asText();
        assertThat(request("PATCH","/api/v1/users/"+id,Map.of("is_active",false),a).status()).isEqualTo(200);
        assertThat(post("login",Map.of("email","reader@example.com","password","Password123!")).status()).isEqualTo(401);
        assertThat(request("GET","/api/v1/users/me",null,access(t)).status()).isEqualTo(401);
        assertThat(request("DELETE","/api/v1/users/me",null,a).status()).isEqualTo(409);
        assertThat(request("PATCH","/api/v1/users/"+admin.id(),Map.of("roles",List.of(1)),a).status()).isEqualTo(409);
        assertThat(request("GET","/api/v1/users?size=101",null,a).status()).isEqualTo(400);
    }
    @Test void ownershipAndSoftDelete() throws Exception {
        var one=registerLogin("one@example.com");
        var two=registerLogin("two@example.com");
        String id=request("GET","/api/v1/users/me",null,access(two)).body().at("/data/id").asText();
        assertThat(request("GET","/api/v1/users/"+id,null,access(one)).status()).isEqualTo(403);
        assertThat(request("GET","/api/v1/users/"+id,null,access(two)).status()).isEqualTo(200);
        assertThat(request("DELETE","/api/v1/users/me",null,access(two)).status()).isEqualTo(204);
        assertThat(post("login",Map.of("email","two@example.com","password","Password123!")).status()).isEqualTo(401);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users WHERE deleted_at IS NOT NULL",Integer.class)).isEqualTo(1);
    }
    @Test void logoutAllAndExpiredRefresh() throws Exception {
        var t=registerLogin("reader@example.com");
        assertThat(request("POST","/api/v1/auth/logout-all",null,access(t)).status()).isEqualTo(200);
        assertThat(request("GET","/api/v1/users/me",null,access(t)).status()).isEqualTo(401);
        var next=post("login",Map.of("email","reader@example.com","password","Password123!")).body().get("data");
        db.update("UPDATE refresh_tokens SET created_at=TIMESTAMP '2000-01-01 00:00:00',expires_at=TIMESTAMP '2000-01-02 00:00:00'");
        assertThat(post("refresh-token",Map.of("refresh_token",refresh(next))).status()).isEqualTo(401);
    }
    @Test void concurrentRefreshAllowsOneRotationAndRevokesOnReuse() throws Exception {
        var t=registerLogin("reader@example.com");
        var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            Callable<Integer> work=()->{start.await(); return post("refresh-token",Map.of("refresh_token",refresh(t))).status();};
            var first=pool.submit(work); var second=pool.submit(work); start.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
            assertThat(request("GET","/api/v1/users/me",null,access(t)).status()).isEqualTo(401);
        } finally { pool.shutdownNow(); }
    }
    @Test void rejectsForgedTokenAndOversizeUtf8Password() throws Exception {
        assertThat(request("GET","/api/v1/users/me",null,"invalid").status()).isEqualTo(401);
        assertThat(post("register",Map.of("email","reader@example.com","password","ậ".repeat(30))).status()).isEqualTo(400);
    }

    @Test void adminCreationAndRoleChangeInvalidateOldTokens() throws Exception {
        accounts.adminCreate(new Contracts.AdminCreate("admin@example.com",null,"Password123!",List.of(2)));
        String admin=access(post("login",Map.of("email","admin@example.com","password","Password123!")).body().get("data"));
        var created=request("POST","/api/v1/users",Map.of("email","staff@example.com","password","Password123!","roles",List.of(1)),admin);
        assertThat(created.status()).isEqualTo(201);
        String id=created.body().at("/data/id").asText();
        String staff=access(post("login",Map.of("email","staff@example.com","password","Password123!")).body().get("data"));
        assertThat(request("PATCH","/api/v1/users/"+id,Map.of("roles",List.of(1,2)),admin).status()).isEqualTo(200);
        assertThat(request("GET","/api/v1/users/me",null,staff).status()).isEqualTo(401);
        String updated=access(post("login",Map.of("email","staff@example.com","password","Password123!")).body().get("data"));
        assertThat(request("GET","/api/v1/users",null,updated).status()).isEqualTo(200);
    }

    @Test void deletedUserCannotRefreshAndFormLoginWorks() throws Exception {
        var t=registerLogin("reader@example.com");
        var form=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/login/access-token"))
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=reader%40example.com&password=Password123%21")).build();
        var response=http.send(form,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).has("access_token")).isTrue();
        assertThat(request("DELETE","/api/v1/users/me",null,access(t)).status()).isEqualTo(204);
        assertThat(post("refresh-token",Map.of("refresh_token",refresh(t))).status()).isEqualTo(401);
    }

    @Test void jpaVersionAndOutboxRemainConsistentAndStaleWritesFail() throws Exception {
        var token = registerLogin("version@example.com");
        UUID id = UUID.fromString(request("GET", "/api/v1/users/me", null, access(token)).body().at("/data/id").asText());
        long before = db.queryForObject("SELECT version FROM users WHERE id=?", Long.class, id);
        assertThat(request("PATCH", "/api/v1/users/me", Map.of("full_name", "Changed"), access(token)).status()).isEqualTo(200);
        long after = db.queryForObject("SELECT version FROM users WHERE id=?", Long.class, id);
        assertThat(after).isGreaterThan(before);
        assertThat(db.queryForObject("SELECT aggregate_version FROM outbox_events WHERE aggregate_id=? AND event_type='UserUpdated'", Long.class, id)).isEqualTo(after);

        var first = entityManagerFactory.createEntityManager();
        var second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var one = first.find(UserEntity.class, id);
            var two = second.find(UserEntity.class, id);
            one.setFullName("First");
            first.getTransaction().commit();
            two.setFullName("Stale");
            assertThatThrownBy(() -> second.getTransaction().commit())
                    .isInstanceOf(jakarta.persistence.RollbackException.class);
            assertThat(db.queryForObject("SELECT full_name FROM users WHERE id=?", String.class, id)).isEqualTo("First");
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close();
            second.close();
        }
    }
}
