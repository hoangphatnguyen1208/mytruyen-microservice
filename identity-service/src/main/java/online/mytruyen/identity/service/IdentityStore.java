package online.mytruyen.identity.service;

import online.mytruyen.identity.domain.CredentialEntity;
import online.mytruyen.identity.domain.OutboxEventEntity;
import online.mytruyen.identity.domain.RoleEntity;
import online.mytruyen.identity.domain.UserEntity;
import online.mytruyen.identity.exception.ApiError;
import online.mytruyen.identity.repository.CredentialRepository;
import online.mytruyen.identity.repository.OutboxEventRepository;
import online.mytruyen.identity.repository.RoleRepository;
import online.mytruyen.identity.repository.SessionRepository;
import online.mytruyen.identity.repository.UserRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.util.*;
import static online.mytruyen.identity.dto.Contracts.*;

@Component
public class IdentityStore {
    private final UserRepository users;
    private final RoleRepository roles;
    private final CredentialRepository credentials;
    private final SessionRepository sessions;
    private final OutboxEventRepository events;

    public IdentityStore(UserRepository users, RoleRepository roles, CredentialRepository credentials,
                         SessionRepository sessions, OutboxEventRepository events) {
        this.users = users;
        this.roles = roles;
        this.credentials = credentials;
        this.sessions = sessions;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<String> roles(UUID id) { return roleCodes(required(id)); }

    private List<String> roleCodes(UserEntity user) {
        return user.getRoles().stream().sorted(Comparator.comparing(RoleEntity::getId))
                .map(RoleEntity::getCode).toList();
    }

    @Transactional(readOnly = true)
    public UserView user(UUID id) { return view(required(id)); }

    UserView view(UserEntity user) {
        return new UserView(user.getId(), user.getEmail(), user.getUsername(), user.getFullName(),
                user.isActive(), roleCodes(user), user.getCreatedAt(), user.getUpdatedAt());
    }

    private UserEntity required(UUID id) {
        return users.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ApiError(404, "User not found"));
    }

    public UserEntity lock(UUID id) {
        UserEntity user = lockIncludingDeleted(id);
        if (user.getDeletedAt() != null) throw new ApiError(404, "User not found");
        return user;
    }

    public UserEntity lockIncludingDeleted(UUID id) {
        return users.lockById(id).orElseThrow(() -> new ApiError(404, "User not found"));
    }

    public Optional<UUID> loginId(String email) { return users.findLoginId(email); }
    public void lockAdminPolicy() { roles.lockAdminRole(); }
    public long activeAdmins(UUID excluded) { return users.countActiveAdmins(excluded); }
    public void revoke(UUID id) { sessions.revokeAll(id, Instant.now()); }

    public CredentialEntity credential(UUID id) {
        return credentials.findById(id).orElseThrow(() -> new ApiError(401, "Invalid credentials"));
    }

    public UserEntity create(String email, String username, String hash, List<Integer> roleIds) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setUsername(username);
        setRoles(user, roleIds);
        users.saveAndFlush(user);
        CredentialEntity credential = new CredentialEntity();
        credential.setUser(user);
        credential.setPasswordHash(hash);
        credential.setPasswordChangedAt(Instant.now());
        credentials.save(credential);
        return user;
    }

    public void setRoles(UserEntity user, List<Integer> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id < 1 || id > 3))
            throw new ApiError(400, "Roles must contain IDs 1 (USER), 2 (ADMIN) or 3 (IMPORTER)");
        var values = roles.findAllById(ids.stream().distinct().map(Integer::shortValue).toList());
        if (values.size() != new HashSet<>(ids).size()) throw new ApiError(400, "Role not found");
        user.getRoles().clear();
        user.getRoles().addAll(values);
    }

    // Dirty the aggregate even when only credentials changed. Hibernate owns @Version.
    public void touch(UserEntity user) { user.setUpdatedAt(Instant.now()); }

    public void event(UserEntity user, String type) {
        // Flush first so the event records Hibernate's actual aggregate version.
        users.flush();
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId(UUID.randomUUID());
        event.setAggregateId(user.getId());
        event.setAggregateVersion(user.getVersion());
        event.setEventType(type);
        event.setPayload("{\"user_id\":\"" + user.getId() + "\"}");
        event.setOccurredAt(Instant.now());
        events.save(event);
    }

    @Transactional(readOnly = true)
    public UserPage page(int page, int size) {
        var result = users.findByDeletedAtIsNull(PageRequest.of(page, size, Sort.by("createdAt", "id")));
        return new UserPage(200, true, "Success", result.getContent().stream().map(this::view).toList(),
                new Pagination(page, size, result.getTotalElements(), result.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public boolean activeSession(UUID userId, UUID sessionId) {
        return sessions.active(userId, sessionId, Instant.now());
    }
}
