package online.mytruyen.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import online.mytruyen.identity.dto.Contracts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer, audience;
    private final long ttl;

    public JwtService(@Value("${jwt.private-key}") String privateValue,
                      @Value("${jwt.public-key}") String publicValue,
                      @Value("${jwt.issuer}") String issuer, @Value("${jwt.audience}") String audience,
                      @Value("${jwt.access-seconds:900}") long ttl) throws Exception {
        var factory = KeyFactory.getInstance("RSA");
        privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateValue)));
        publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicValue)));
        if (!privateKey.getModulus().equals(publicKey.getModulus()) || publicKey.getModulus().bitLength() < 2048 || ttl < 1)
            throw new IllegalArgumentException("Invalid JWT key pair or lifetime");
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
    }

    public long accessTokenTtl() {
        return ttl;
    }

    public String issue(Contracts.UserView user, UUID sessionId) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject(user.id().toString()).setIssuer(issuer).setAudience(audience)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(ttl))).setId(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString()).claim("roles", user.roles().stream().map(r -> "ROLE_" + r).toList())
                .signWith(privateKey, SignatureAlgorithm.RS256).compact();
    }

    public Claims verify(String token) {
        var signed = Jwts.parserBuilder().setSigningKey(publicKey).requireIssuer(issuer).requireAudience(audience).build().parseClaimsJws(token);
        var c = signed.getBody();
        if (!"RS256".equals(signed.getHeader().getAlgorithm()) || c.getExpiration() == null ||
                c.getIssuedAt() == null || c.getIssuedAt().after(new Date()) || c.getId() == null ||
                c.getSubject() == null || c.get("sid", String.class) == null)
            throw new JwtException("Required claims missing or invalid");
        UUID.fromString(c.getSubject());
        UUID.fromString(c.get("sid", String.class));
        return c;
    }
}
