package online.mytruyen.identity;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static online.mytruyen.identity.Contracts.*;

@Service
public class AuthService {
    private final IdentityStore store;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final long days;
    private final SecureRandom random=new SecureRandom();
    private final String dummyHash;
    public AuthService(IdentityStore store,PasswordEncoder passwords,JwtService jwt,
            @Value("${identity.refresh-days:7}") long days) {
        if(days<1 || days>365) throw new IllegalArgumentException("Refresh days must be between 1 and 365");
        this.store=store; this.passwords=passwords; this.jwt=jwt; this.days=days;
        dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    @Transactional
    public Token login(Login input) {
        if(input.password().getBytes(StandardCharsets.UTF_8).length>72) throw new ApiError(401,"Invalid credentials");
        var ids=store.jdbc().queryForList("SELECT id FROM users WHERE email=? AND deleted_at IS NULL",UUID.class,AccountService.normalize(input.email()));
        if(ids.isEmpty()) { passwords.matches(input.password(),dummyHash); throw new ApiError(401,"Invalid credentials"); }
        UUID id=ids.get(0); store.lock(id);
        UserView user=store.user(id);
        String hash=store.jdbc().queryForObject("SELECT password_hash FROM user_credentials WHERE user_id=?",String.class,id);
        if(!passwords.matches(input.password(),hash) || !user.is_active()) throw new ApiError(401,"Invalid credentials");
        UUID sid=UUID.randomUUID(); Instant expiry=Instant.now().plus(days,ChronoUnit.DAYS);
        store.jdbc().update("INSERT INTO auth_sessions(id,user_id,expires_at) VALUES (?,?,?)",sid,id,Timestamp.from(expiry));
        return issue(user,sid,expiry);
    }
    record Stored(UUID id,UUID sid,UUID userId,Instant expiry,boolean used,boolean revoked) {}
    private Stored find(String raw) {
        var rows=store.jdbc().query("SELECT t.*,s.user_id FROM refresh_tokens t JOIN auth_sessions s ON t.session_id=s.id WHERE token_hash=?",
            (r,n)->new Stored(r.getObject("id",UUID.class),r.getObject("session_id",UUID.class),r.getObject("user_id",UUID.class),
                r.getTimestamp("expires_at").toInstant(),r.getTimestamp("used_at")!=null,r.getTimestamp("revoked_at")!=null),digest(raw));
        if(rows.isEmpty()) throw new ApiError(401,"Invalid refresh token");
        return rows.get(0);
    }
    public static class Reuse extends ApiError { public Reuse() { super(401,"Refresh token reuse detected; session revoked"); } }
    @Transactional(noRollbackFor=Reuse.class)
    public Token refresh(String raw) {
        Stored first=find(raw);
        // All session mutations lock user before session, including password change and account disable.
        try { store.lock(first.userId()); }
        catch (ApiError e) { throw new ApiError(401,"Invalid refresh token"); }
        store.jdbc().queryForList("SELECT id FROM auth_sessions WHERE id=? FOR UPDATE",first.sid());
        Stored token=find(raw);
        if(token.used()) {
            store.jdbc().update("UPDATE auth_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE id=?",token.sid());
            throw new Reuse();
        }
        if(token.revoked() || !token.expiry().isAfter(Instant.now()) || !store.activeSession(token.userId(),token.sid()))
            throw new ApiError(401,"Invalid refresh token");
        store.jdbc().update("UPDATE refresh_tokens SET used_at=CURRENT_TIMESTAMP WHERE id=?",token.id());
        return issue(store.user(token.userId()),token.sid(),token.expiry());
    }
    @Transactional
    public void logout(String raw) {
        Stored token;
        try { token=find(raw); } catch(ApiError e) { return; }
        // Also succeeds for previously rotated tokens, revoking their whole family.
        store.jdbc().queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",token.userId());
        store.jdbc().update("UPDATE auth_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE id=? AND revoked_at IS NULL",token.sid());
    }
    @Transactional
    public void logoutAll(UUID id) { store.lock(id); store.revoke(id); }
    private Token issue(UserView user,UUID sid,Instant expiry) {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.jdbc().update("INSERT INTO refresh_tokens(id,session_id,token_hash,expires_at) VALUES (?,?,?,?)",
            UUID.randomUUID(),sid,digest(raw),Timestamp.from(expiry));
        return new Token(jwt.issue(user,sid),raw,"bearer",jwt.ttl);
    }
    static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
