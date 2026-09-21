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
                MockServerHttpRequest.get("/api/v1/books").build()
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
                MockServerHttpRequest.get("/api/v1/books")
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
}
