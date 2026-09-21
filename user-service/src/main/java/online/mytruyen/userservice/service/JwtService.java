package online.mytruyen.userservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import online.mytruyen.userservice.security.JwtConfig;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

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

    private PublicKey readPublicKey() {
        try {
            byte[] keyBytes = Base64.getDecoder()
                    .decode(jwtConfig.getPublicKey().replaceAll("\\s", ""));
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT public key", exception);
        }
    }
}
