package online.mytruyen.identity;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.UUID;
import static online.mytruyen.identity.Contracts.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final AccountService accounts;
    public UserController(AccountService accounts) { this.accounts=accounts; }
    @GetMapping @PreAuthorize("hasRole('ADMIN')")
    public UserPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return accounts.list(page,size); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED)
    public Envelope<UserView> create(@Valid @RequestBody AdminCreate request) { return Envelope.ok(201,accounts.adminCreate(request)); }
    @GetMapping("/me")
    public Envelope<UserView> me(@AuthenticationPrincipal IdentityPrincipal p) { return Envelope.ok(200,accounts.get(p.userId())); }
    @PatchMapping("/me")
    public Envelope<UserView> profile(@AuthenticationPrincipal IdentityPrincipal p,@Valid @RequestBody Profile request) { return Envelope.ok(200,accounts.profile(p.userId(),request)); }
    @DeleteMapping("/me") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal IdentityPrincipal p) { accounts.delete(p.userId()); }
    @PostMapping("/me/password")
    public Envelope<Void> password(@AuthenticationPrincipal IdentityPrincipal p,@Valid @RequestBody Password request) { accounts.password(p.userId(),request); return Envelope.ok(200,null); }
    @GetMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.userId()")
    public Envelope<UserView> get(@PathVariable UUID id) { return Envelope.ok(200,accounts.get(id)); }
    @PatchMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Envelope<UserView> update(@PathVariable UUID id,@Valid @RequestBody AdminUpdate request) { return Envelope.ok(200,accounts.adminUpdate(id,request)); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { accounts.delete(id); }
}
