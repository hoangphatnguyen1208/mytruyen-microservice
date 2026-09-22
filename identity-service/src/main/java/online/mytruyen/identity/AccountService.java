package online.mytruyen.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static online.mytruyen.identity.Contracts.*;

@Service
public class AccountService {
    private final IdentityStore store;
    private final PasswordEncoder passwords;

    public AccountService(IdentityStore store, PasswordEncoder passwords) {
        this.store = store;
        this.passwords = passwords;
    }

    static String normalize(String value) { return value == null ? null : value.trim().toLowerCase(Locale.ROOT); }

    static void validPassword(String value) {
        if (value == null || value.length() < 8 || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiError(400, "Password must have at least 8 characters and at most 72 UTF-8 bytes");
    }

    @Transactional
    public UserView register(Register input) { return create(input.email(), input.username(), input.password(), List.of(1)); }

    @Transactional
    public UserView adminCreate(AdminCreate input) { return create(input.email(), input.username(), input.password(), input.roles()); }

    private UserView create(String email, String username, String password, List<Integer> roles) {
        validPassword(password);
        UserEntity user = store.create(normalize(email), normalize(username), passwords.encode(password), roles);
        store.event(user, "UserCreated");
        return store.view(user);
    }

    public UserView get(UUID id) { return store.user(id); }

    public UserPage list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiError(400, "page must be >= 0 and size between 1 and 100");
        return store.page(page, size);
    }

    @Transactional
    public UserView profile(UUID id, Profile input) {
        UserEntity user = store.lock(id);
        if (input.username() != null) user.setUsername(normalize(input.username()));
        if (input.full_name() != null) user.setFullName(input.full_name().trim());
        store.touch(user);
        store.event(user, "UserUpdated");
        return store.view(user);
    }

    private void keepAdmin(UserEntity user) {
        if (!user.isActive() || user.getRoles().stream().noneMatch(r -> r.getId() == 2)) return;
        if (store.activeAdmins(user.getId()) == 0) throw new ApiError(409, "Cannot remove the last active administrator");
    }

    @Transactional
    public UserView adminUpdate(UUID id, AdminUpdate input) {
        store.lockAdminPolicy();
        UserEntity user = store.lock(id);
        if (Boolean.FALSE.equals(input.is_active()) || (input.roles() != null && !input.roles().contains(2))) keepAdmin(user);
        if (input.is_active() != null) user.setActive(input.is_active());
        if (input.full_name() != null) user.setFullName(input.full_name().trim());
        if (input.roles() != null) store.setRoles(user, input.roles());
        if (input.is_active() != null || input.roles() != null) store.revoke(id);
        store.touch(user);
        store.event(user, Boolean.FALSE.equals(input.is_active()) ? "UserDeactivated" : "UserUpdated");
        return store.view(user);
    }

    @Transactional
    public void delete(UUID id) {
        store.lockAdminPolicy();
        UserEntity user = store.lock(id);
        keepAdmin(user);
        user.setActive(false);
        user.setDeletedAt(Instant.now());
        store.revoke(id);
        store.touch(user);
        store.event(user, "UserDeleted");
    }

    @Transactional
    public void password(UUID id, Password input) {
        validPassword(input.new_password());
        UserEntity user = store.lock(id);
        CredentialEntity credential = store.credential(id);
        if (!passwords.matches(input.current_password(), credential.getPasswordHash())) throw new ApiError(401, "Invalid current password");
        credential.setPasswordHash(passwords.encode(input.new_password()));
        credential.setPasswordChangedAt(Instant.now());
        store.revoke(id);
        store.touch(user);
        store.event(user, "UserCredentialsChanged");
    }
}
