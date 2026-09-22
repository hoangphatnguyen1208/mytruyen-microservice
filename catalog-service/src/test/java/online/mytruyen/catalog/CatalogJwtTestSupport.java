package online.mytruyen.catalog;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import io.jsonwebtoken.*;
import java.security.*;
import java.util.*;
import java.time.Instant;

abstract class CatalogJwtTestSupport {
    static final KeyPair KEYS;
    static final UUID ADMIN_ID=UUID.randomUUID();
    static {
        try { var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); KEYS=generator.generateKeyPair(); }
        catch (GeneralSecurityException e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        registry.add("jwt.issuer", () -> "mytruyen-auth");
        registry.add("jwt.audience", () -> "mytruyen-api");
    }
    static String token(String role) {
        return Jwts.builder().setSubject(ADMIN_ID.toString()).setIssuer("mytruyen-auth").setAudience("mytruyen-api")
            .setIssuedAt(new Date()).setExpiration(Date.from(Instant.now().plusSeconds(300)))
            .claim("roles",List.of(role)).signWith(KEYS.getPrivate(),SignatureAlgorithm.RS256).compact();
    }
}

