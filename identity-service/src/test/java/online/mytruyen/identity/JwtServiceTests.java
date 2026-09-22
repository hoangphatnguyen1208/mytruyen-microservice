package online.mytruyen.identity;

import io.jsonwebtoken.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.security.*;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTests {
    final KeyPair keys=IdentityIntegrationTests.keys();
    JwtService service() throws Exception {
        return new JwtService(Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
            Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),"issuer","audience",900);
    }
    String token(KeyPair pair,String issuer,String audience,Date expiry,boolean issuedAt,SignatureAlgorithm algorithm) {
        var b=Jwts.builder().setSubject(UUID.randomUUID().toString()).setId(UUID.randomUUID().toString())
            .setIssuer(issuer).setAudience(audience).claim("sid",UUID.randomUUID().toString());
        if(expiry!=null) b.setExpiration(expiry);
        if(issuedAt) b.setIssuedAt(new Date());
        return b.signWith(pair.getPrivate(),algorithm).compact();
    }
    @Test void rejectsWrongSignatureIssuerAudienceAlgorithmAndMissingClaims() throws Exception {
        var verifier=service();
        Date future=new Date(System.currentTimeMillis()+60000);
        for(String value:List.of(
            token(IdentityIntegrationTests.keys(),"issuer","audience",future,true,SignatureAlgorithm.RS256),
            token(keys,"other","audience",future,true,SignatureAlgorithm.RS256),
            token(keys,"issuer","other",future,true,SignatureAlgorithm.RS256),
            token(keys,"issuer","audience",new Date(1000),true,SignatureAlgorithm.RS256),
            token(keys,"issuer","audience",null,true,SignatureAlgorithm.RS256),
            token(keys,"issuer","audience",future,false,SignatureAlgorithm.RS256),
            token(keys,"issuer","audience",future,true,SignatureAlgorithm.RS512)))
            assertThatThrownBy(()->verifier.verify(value)).isInstanceOf(JwtException.class);
    }
    @Test void refusesMismatchedKeyPairAtStartup() {
        assertThatThrownBy(()->new JwtService(
            Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
            Base64.getEncoder().encodeToString(IdentityIntegrationTests.keys().getPublic().getEncoded()),"issuer","audience",900))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsMissingSubjectOrSession() throws Exception {
        var verifier=service();
        for(boolean omitSubject:List.of(true,false)) {
            var builder=Jwts.builder().setIssuer("issuer").setAudience("audience")
                .setId(UUID.randomUUID().toString()).setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis()+60000));
            if(omitSubject) builder.claim("sid",UUID.randomUUID().toString());
            else builder.setSubject(UUID.randomUUID().toString());
            String value=builder.signWith(keys.getPrivate(),SignatureAlgorithm.RS256).compact();
            assertThatThrownBy(()->verifier.verify(value)).isInstanceOf(JwtException.class);
        }
    }
}
