package online.mytruyen.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final IdentityStore store;
    private final AccountService accounts;
    private final boolean enabled;
    private final String email,password;
    public BootstrapAdmin(IdentityStore store,AccountService accounts,
            @Value("${identity.bootstrap.enabled:false}") boolean enabled,
            @Value("${identity.bootstrap.email:}") String email,
            @Value("${identity.bootstrap.password:}") String password) {
        this.store=store; this.accounts=accounts; this.enabled=enabled; this.email=email; this.password=password;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if(!enabled) return;
        if(email.isBlank() || !email.contains("@") || email.length()>254) throw new IllegalArgumentException("Valid bootstrap email required");
        AccountService.validPassword(password);
        store.jdbc().queryForList("SELECT id FROM roles WHERE id=2 FOR UPDATE");
        long count=store.jdbc().queryForObject("SELECT COUNT(*) FROM users u JOIN user_roles r ON u.id=r.user_id WHERE r.role_id=2 AND u.is_active=TRUE AND u.deleted_at IS NULL",Long.class);
        if(count==0) accounts.adminCreate(new Contracts.AdminCreate(email,null,password,List.of(1,2)));
    }
}
