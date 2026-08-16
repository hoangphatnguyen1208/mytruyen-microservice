package online.mytruyen.mytruyengateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.security.Key;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

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
        Key key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(properties.getIssuer())
                .requireAudience(properties.getAudience())
                .build()
                .parseClaimsJws(token)
                .getBody();
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
        if (path.startsWith("/actuator/")) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && path.equals("/api/v1/auth/health")) {
            return true;
        }
        return HttpMethod.POST.equals(method) && (
                path.equals("/api/v1/auth/login")
                        || path.equals("/api/v1/auth/login/access-token")
                        || path.equals("/api/v1/auth/register")
                        || path.equals("/api/v1/auth/refresh-token")
        );
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
