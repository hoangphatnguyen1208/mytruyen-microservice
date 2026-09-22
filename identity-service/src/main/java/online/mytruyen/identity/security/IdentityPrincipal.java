package online.mytruyen.identity.security;
import java.util.UUID;
public record IdentityPrincipal(UUID userId, UUID sessionId) {}
