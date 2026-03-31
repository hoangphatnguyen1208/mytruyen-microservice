package online.mytruyen.authservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import online.mytruyen.authservice.security.JwtConfig;
import online.mytruyen.authservice.security.JwtConfig;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtConfig jwtConfig;

    private Claims extractAllClaims(String token) {
        Key key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractId(String token) {
        return extractAllClaims(token).getSubject();
    }

    private Boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public Boolean isTokenValid(String token, String id) {
        final String extractedId = extractId(token);
        return (extractedId.equals(id) && !isTokenExpired(token));
    }
}
