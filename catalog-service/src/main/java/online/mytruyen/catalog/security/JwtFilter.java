package online.mytruyen.catalog.security;

import io.jsonwebtoken.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtParser parser;
    public JwtFilter(@Value("${jwt.public-key}") String key,
                     @Value("${jwt.issuer}") String issuer, @Value("${jwt.audience}") String audience) throws Exception {
        var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
        if (publicKey.getModulus().bitLength() < 2048) throw new IllegalArgumentException("RSA key must be at least 2048 bits");
        parser = Jwts.parserBuilder().setSigningKey(publicKey).requireIssuer(issuer).requireAudience(audience).build();
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw new JwtException("Invalid authorization");
                var parsed = parser.parseClaimsJws(header.substring(7));
                var claims = parsed.getBody();
                if (!"RS256".equals(parsed.getHeader().getAlgorithm()) || claims.getExpiration() == null ||
                    claims.getIssuedAt() == null || claims.getIssuedAt().after(new Date()) || claims.getSubject() == null)
                    throw new JwtException("Invalid claims");
                UUID id = UUID.fromString(claims.getSubject());
                Object value = claims.get("roles");
                if (!(value instanceof List<?> roles) || roles.stream().anyMatch(r -> !(r instanceof String)))
                    throw new JwtException("Invalid roles");
                var authorities = roles.stream().map(r -> new SimpleGrantedAuthority((String) r)).toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id, null, authorities));
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                failure(response, 401, "Invalid or expired token");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    static void failure(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status_code\":" + status + ",\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }
}
