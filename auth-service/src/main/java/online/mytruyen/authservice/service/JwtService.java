package online.mytruyen.authservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import online.mytruyen.authservice.security.JwtConfig;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtConfig jwtConfig;

    private Claims extractAllClaims(String token) {
        Jws<Claims> parsed = Jwts.parserBuilder()
                .setSigningKey(readPublicKey())
                .requireIssuer(jwtConfig.getIssuer())
                .requireAudience(jwtConfig.getAudience())
                .build()
                .parseClaimsJws(token);
        if (!jwtConfig.getAlgorithm().equals(parsed.getHeader().getAlgorithm())) {
            throw new JwtException("Unexpected JWT algorithm");
        }
        return parsed.getBody();
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

    public String generateToken(String id, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();

        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(id)
                .setIssuer(jwtConfig.getIssuer())
                .setAudience(jwtConfig.getAudience())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(readPrivateKey())
                .compact();
    }

    private PrivateKey readPrivateKey() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(normalize(jwtConfig.getPrivateKey()));
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT private key", exception);
        }
    }

    private PublicKey readPublicKey() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(normalize(jwtConfig.getPublicKey()));
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT public key", exception);
        }
    }

    private String normalize(String key) {
        return key.replaceAll("\\s", "");
    }
}
