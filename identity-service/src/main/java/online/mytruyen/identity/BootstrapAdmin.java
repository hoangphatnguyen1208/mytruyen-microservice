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
        store.lockAdminPolicy();
        long count=store.activeAdmins(null);
        if(count==0) accounts.adminCreate(new Contracts.AdminCreate(email,null,password,List.of(1,2)));
    }
}
