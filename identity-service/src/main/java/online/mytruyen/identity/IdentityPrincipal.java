package online.mytruyen.identity;
import java.util.UUID;
public record IdentityPrincipal(UUID userId, UUID sessionId) {}
