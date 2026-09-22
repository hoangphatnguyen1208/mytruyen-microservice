package online.mytruyen.identity;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.security.*;
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
    private final SessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final long days;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(IdentityStore store, PasswordEncoder passwords, JwtService jwt,
                       SessionRepository sessions, RefreshTokenRepository tokens,
                       @Value("${identity.refresh-days:7}") long days) {
        if (days < 1 || days > 365) throw new IllegalArgumentException("Refresh days must be between 1 and 365");
        this.store = store;
        this.passwords = passwords;
        this.jwt = jwt;
        this.sessions = sessions;
        this.tokens = tokens;
        this.days = days;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Token login(Login input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new ApiError(401, "Invalid credentials");
        Optional<UUID> id = store.loginId(AccountService.normalize(input.email()));
        if (id.isEmpty()) {
            passwords.matches(input.password(), dummyHash);
            throw new ApiError(401, "Invalid credentials");
        }
        UserEntity user = store.lock(id.get());
        if (!passwords.matches(input.password(), store.credential(user.getId()).getPasswordHash()) || !user.isActive())
            throw new ApiError(401, "Invalid credentials");
        SessionEntity session = new SessionEntity();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(session.getCreatedAt().plus(days, ChronoUnit.DAYS));
        session = sessions.save(session);
        return issue(store.view(user), session);
    }

    public static class Reuse extends ApiError {
        public Reuse() { super(401, "Refresh token reuse detected; session revoked"); }
    }

    @Transactional(noRollbackFor = Reuse.class)
    public Token refresh(String raw) {
        var reference = tokens.reference(digest(raw)).orElseThrow(() -> new ApiError(401, "Invalid refresh token"));
        // Scalar lookup above deliberately does not hydrate the token before acquiring locks.
        UserEntity user;
        try { user = store.lock(reference.getUserId()); }
        catch (ApiError e) { throw new ApiError(401, "Invalid refresh token"); }
        SessionEntity session = sessions.lockById(reference.getSessionId()).orElseThrow(() -> new ApiError(401, "Invalid refresh token"));
        RefreshTokenEntity token = tokens.lockByHash(digest(raw)).orElseThrow(() -> new ApiError(401, "Invalid refresh token"));
        if (token.getUsedAt() != null) {
            session.setRevokedAt(Instant.now());
            throw new Reuse();
        }
        Instant now = Instant.now();
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now) || session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(now) || !user.isActive())
            throw new ApiError(401, "Invalid refresh token");
        token.setUsedAt(now);
        return issue(store.view(user), session);
    }

    @Transactional
    public void logout(String raw) {
        var reference = tokens.reference(digest(raw));
        if (reference.isEmpty()) return;
        store.lockIncludingDeleted(reference.get().getUserId());
        sessions.lockById(reference.get().getSessionId()).ifPresent(session -> {
            if (session.getRevokedAt() == null) session.setRevokedAt(Instant.now());
        });
    }

    @Transactional
    public void logoutAll(UUID id) { store.lock(id); store.revoke(id); }

    private Token issue(UserView user, SessionEntity session) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setId(UUID.randomUUID());
        token.setSession(session);
        token.setTokenHash(digest(raw));
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(session.getExpiresAt());
        tokens.save(token);
        return new Token(jwt.issue(user, session.getId()), raw, "bearer", jwt.ttl);
    }

    static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
