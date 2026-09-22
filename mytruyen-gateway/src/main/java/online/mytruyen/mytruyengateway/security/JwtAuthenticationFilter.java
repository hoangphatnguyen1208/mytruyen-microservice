package online.mytruyen.mytruyengateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final Set<String> PUBLIC_GET_PREFIXES = Set.of(
            "/api/v1/books",
            "/api/v1/chapters",
            "/api/v1/genres",
            "/api/v1/tags",
            "/api/v1/authors",
            "/api/v1/book-statuses",
            "/api/v1/search"
    );

    private final JwtProperties properties;

    public JwtAuthenticationFilter(JwtProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = removeIdentityHeaders(exchange);
        if (isPublicRequest(sanitizedExchange)) {
            return chain.filter(sanitizedExchange);
        }

        String authorization = sanitizedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(sanitizedExchange, "Missing bearer token");
        }

        try {
            Claims claims = parseClaims(authorization.substring(7));
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return unauthorized(sanitizedExchange, "Token subject is missing");
            }

            Object rolesClaim = claims.get("roles");
            String roles = rolesClaim instanceof List<?> list
                    ? list.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("")
                    : "";

            ServerWebExchange authenticatedExchange = sanitizedExchange.mutate()
                    .request(request -> request.headers(headers -> {
                        headers.set(USER_ID_HEADER, subject);
                        headers.set(USER_ROLES_HEADER, roles);
                    }))
                    .build();
            return chain.filter(authenticatedExchange);
        } catch (JwtException | IllegalArgumentException exception) {
            return unauthorized(sanitizedExchange, "Invalid or expired token");
        }
    }

    private Claims parseClaims(String token) {
        Jws<Claims> parsed = Jwts.parserBuilder()
                .setSigningKey(readPublicKey())
                .requireIssuer(properties.getIssuer())
                .requireAudience(properties.getAudience())
                .build()
                .parseClaimsJws(token);
        if (!properties.getAlgorithm().equals(parsed.getHeader().getAlgorithm())) {
            throw new JwtException("Unexpected JWT algorithm");
        }
        return parsed.getBody();
    }

    private PublicKey readPublicKey() {
        try {
            byte[] keyBytes = Base64.getDecoder()
                    .decode(properties.getPublicKey().replaceAll("\\s", ""));
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT public key", exception);
        }
    }

    private ServerWebExchange removeIdentityHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                }))
                .build();
    }

    private boolean isPublicRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }

        if (HttpMethod.GET.equals(method)
                && (path.equals("/actuator/health") || path.startsWith("/actuator/health/"))) {
            return true;
        }

        if (HttpMethod.POST.equals(method)) {
            return path.equals("/api/v1/auth/login")
                    || path.equals("/api/v1/auth/register")
                    || path.equals("/api/v1/auth/login/access-token")
                    || path.equals("/api/v1/auth/refresh-token")
                    || path.equals("/api/v1/auth/logout");
        }

        return HttpMethod.GET.equals(method) && PUBLIC_GET_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"status_code\":401,\"success\":false,\"message\":\"" + message
                + "\",\"data\":null}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
