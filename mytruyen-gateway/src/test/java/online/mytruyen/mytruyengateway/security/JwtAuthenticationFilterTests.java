package online.mytruyen.mytruyengateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTests {
    private JwtAuthenticationFilter filter;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        JwtProperties properties = new JwtProperties();
        properties.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        properties.setAlgorithm("RS256");
        properties.setIssuer("mytruyen-auth");
        properties.setAudience("mytruyen-api");
        filter = new JwtAuthenticationFilter(properties);
    }

    @Test
    void rejectsProtectedRequestWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/api/v1/books/id/1").build()
        );

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validatesTokenAndReplacesUntrustedIdentityHeaders() {
        String token = Jwts.builder()
                .setSubject("8ed8b6e8-3cc1-498d-a52a-c470835625c9")
                .setIssuer("mytruyen-auth")
                .setAudience("mytruyen-api")
                .claim("roles", List.of("ROLE_ADMIN"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/api/v1/books/id/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(JwtAuthenticationFilter.USER_ID_HEADER, "forged-user")
                        .header(JwtAuthenticationFilter.USER_ROLES_HEADER, "ROLE_ADMIN,ROLE_SUPERUSER")
                        .build()
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = value -> {
            forwarded.set(value);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ID_HEADER))
                .isEqualTo("8ed8b6e8-3cc1-498d-a52a-c470835625c9");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ROLES_HEADER))
                .isEqualTo("ROLE_ADMIN");
    }

    @Test
    void permitsLoginAndRemovesForgedIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .header(JwtAuthenticationFilter.USER_ID_HEADER, "forged-user")
                        .build()
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, value -> {
            forwarded.set(value);
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ID_HEADER))
                .isNull();
    }

    @Test
    void importLookupIsNotPublic() {
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/internal/import/books/metruyencv/123").build());
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void statisticsArePublicButAdminStatisticsRequireAuthentication() {
        assertPublicRequest(MockServerHttpRequest.get("/api/v1/stats/books/count").build());
        var exchange=MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/admin/catalog/stats/books/count").build());
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void permitsRegistrationWithoutToken() {
        assertPublicRequest(MockServerHttpRequest.post("/api/v1/auth/register").build());
    }

    @Test
    void permitsRefreshAndLogoutButProtectsLogoutAll() {
        for (String path : List.of("refresh-token", "logout", "login/access-token")) {
            assertPublicRequest(MockServerHttpRequest.post("/api/v1/auth/" + path).build());
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/logout-all").build());
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void permitsPublicBookReadsButProtectsMutations() {
        assertPublicRequest(MockServerHttpRequest.get("/api/v1/books/slug/example").build());

        MockServerWebExchange mutation = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/api/v1/books/id/1").build()
        );
        StepVerifier.create(filter.filter(mutation, ignored -> Mono.empty())).verifyComplete();
        assertThat(mutation.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void permitsCorsPreflightWithoutToken() {
        assertPublicRequest(MockServerHttpRequest.options("/api/v1/books").build());
    }

    @Test
    void exposesOnlyActuatorHealthWithoutToken() {
        assertPublicRequest(MockServerHttpRequest.get("/actuator/health/readiness").build());

        MockServerWebExchange info = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/info").build()
        );
        StepVerifier.create(filter.filter(info, ignored -> Mono.empty())).verifyComplete();
        assertThat(info.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        assertRejectedToken(createToken(keyPair, "another-issuer", "mytruyen-api", SignatureAlgorithm.RS256));
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        assertRejectedToken(createToken(keyPair, "mytruyen-auth", "another-audience", SignatureAlgorithm.RS256));
    }

    @Test
    void rejectsTokenSignedByAnotherKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        assertRejectedToken(createToken(generator.generateKeyPair(), "mytruyen-auth", "mytruyen-api", SignatureAlgorithm.RS256));
    }

    @Test
    void rejectsUnexpectedRsaAlgorithm() {
        assertRejectedToken(createToken(keyPair, "mytruyen-auth", "mytruyen-api", SignatureAlgorithm.RS512));
    }

    private String createToken(KeyPair signingKey, String issuer, String audience, SignatureAlgorithm algorithm) {
        return Jwts.builder()
                .setSubject("8ed8b6e8-3cc1-498d-a52a-c470835625c9")
                .setIssuer(issuer)
                .setAudience(audience)
                .claim("roles", List.of("ROLE_ADMIN"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey.getPrivate(), algorithm)
                .compact();
    }

    private void assertRejectedToken(String token) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/api/v1/books/id/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void assertPublicRequest(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean forwarded = new AtomicBoolean();

        StepVerifier.create(filter.filter(exchange, value -> {
            forwarded.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded).isTrue();
    }
}
