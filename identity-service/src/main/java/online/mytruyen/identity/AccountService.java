package online.mytruyen.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static online.mytruyen.identity.Contracts.*;

@Service
public class AccountService {
    private final IdentityStore store;
    private final PasswordEncoder passwords;
    public AccountService(IdentityStore store,PasswordEncoder passwords) { this.store=store; this.passwords=passwords; }
    static String normalize(String value) { return value==null ? null : value.trim().toLowerCase(Locale.ROOT); }
    static void validPassword(String value) {
        if(value==null || value.length()<8 || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ApiError(400,"Password must have at least 8 characters and at most 72 UTF-8 bytes");
    }
    @Transactional
    public UserView register(Register input) { return create(input.email(),input.username(),input.password(),List.of(1)); }
    @Transactional
    public UserView adminCreate(AdminCreate input) { return create(input.email(),input.username(),input.password(),input.roles()); }
    private UserView create(String email,String username,String password,List<Integer> roles) {
        validPassword(password);
        UUID id=UUID.randomUUID();
        store.jdbc().update("INSERT INTO users(id,email,username) VALUES (?,?,?)",id,normalize(email),normalize(username));
        store.jdbc().update("INSERT INTO user_credentials(user_id,password_hash) VALUES (?,?)",id,passwords.encode(password));
        setRoles(id,roles);
        store.event(id,"UserCreated");
        return store.user(id);
    }
    private void setRoles(UUID id,List<Integer> roles) {
        if(roles==null || roles.isEmpty() || roles.stream().anyMatch(r->r==null || (r!=1 && r!=2)))
            throw new ApiError(400,"Roles must contain IDs 1 (USER) or 2 (ADMIN)");
        store.jdbc().update("DELETE FROM user_roles WHERE user_id=?",id);
        for(var role:new HashSet<>(roles)) store.jdbc().update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)",id,role);
    }
    public UserView get(UUID id) { return store.user(id); }
    public UserPage list(int page,int size) {
        if(page<0 || size<1 || size>100) throw new ApiError(400,"page must be >= 0 and size between 1 and 100");
        long count=store.jdbc().queryForObject("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL",Long.class);
        var ids=store.jdbc().queryForList("SELECT id FROM users WHERE deleted_at IS NULL ORDER BY created_at,id LIMIT ? OFFSET ?",UUID.class,size,(long)page*size);
        return new UserPage(200,true,"Success",ids.stream().map(store::user).toList(),new Pagination(page,size,count,(count+size-1)/size));
    }
    @Transactional
    public UserView profile(UUID id,Profile input) {
        store.lock(id);
        if(input.username()!=null) store.jdbc().update("UPDATE users SET username=? WHERE id=?",normalize(input.username()),id);
        if(input.full_name()!=null) store.jdbc().update("UPDATE users SET full_name=? WHERE id=?",input.full_name().trim(),id);
        store.bump(id); store.event(id,"UserUpdated");
        return store.user(id);
    }
    // Serialize admin membership changes to prevent simultaneous removal of the last admins.
    private void lockAdminPolicy() { store.jdbc().queryForList("SELECT id FROM roles WHERE id=2 FOR UPDATE"); }
    private void keepAdmin(UUID id) {
        if(!store.roles(id).contains("ADMIN") || !store.user(id).is_active()) return;
        long others=store.jdbc().queryForObject("SELECT COUNT(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE r.role_id=2 AND u.is_active=TRUE AND u.deleted_at IS NULL AND u.id<>?",Long.class,id);
        if(others==0) throw new ApiError(409,"Cannot remove the last active administrator");
    }
    @Transactional
    public UserView adminUpdate(UUID id,AdminUpdate input) {
        lockAdminPolicy(); store.lock(id);
        if(Boolean.FALSE.equals(input.is_active()) || (input.roles()!=null && !input.roles().contains(2))) keepAdmin(id);
        if(input.is_active()!=null) store.jdbc().update("UPDATE users SET is_active=? WHERE id=?",input.is_active(),id);
        if(input.full_name()!=null) store.jdbc().update("UPDATE users SET full_name=? WHERE id=?",input.full_name().trim(),id);
        if(input.roles()!=null) setRoles(id,input.roles());
        if(input.is_active()!=null || input.roles()!=null) store.revoke(id);
        store.bump(id); store.event(id,Boolean.FALSE.equals(input.is_active())?"UserDeactivated":"UserUpdated");
        return store.user(id);
    }
    @Transactional
    public void delete(UUID id) {
        lockAdminPolicy(); store.lock(id); keepAdmin(id);
        store.jdbc().update("UPDATE users SET is_active=FALSE,deleted_at=CURRENT_TIMESTAMP WHERE id=?",id);
        store.revoke(id); store.bump(id); store.event(id,"UserDeleted");
    }
    @Transactional
    public void password(UUID id,Password input) {
        validPassword(input.new_password()); store.lock(id);
        String hash=store.jdbc().queryForObject("SELECT password_hash FROM user_credentials WHERE user_id=?",String.class,id);
        if(!passwords.matches(input.current_password(),hash)) throw new ApiError(401,"Invalid current password");
        store.jdbc().update("UPDATE user_credentials SET password_hash=?,password_changed_at=CURRENT_TIMESTAMP WHERE user_id=?",passwords.encode(input.new_password()),id);
        store.revoke(id); store.bump(id); store.event(id,"UserCredentialsChanged");
    }
}
