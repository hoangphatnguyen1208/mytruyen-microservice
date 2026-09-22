package online.mytruyen.identity;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import static online.mytruyen.identity.Contracts.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final AccountService accounts;
    public AuthController(AuthService auth,AccountService accounts) { this.auth=auth; this.accounts=accounts; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Envelope<UserView> register(@Valid @RequestBody Register request) { return Envelope.ok(201,accounts.register(request)); }
    @PostMapping("/login")
    public Envelope<Token> login(@Valid @RequestBody Login request) { return Envelope.ok(200,auth.login(request)); }
    @PostMapping(value="/login/access-token",consumes="application/x-www-form-urlencoded")
    public Token formLogin(@RequestParam String username,@RequestParam String password) {
        return auth.login(new Login(username,password));
    }
    @PostMapping("/refresh-token")
    public Envelope<Token> refresh(@Valid @RequestBody Refresh request) { return Envelope.ok(200,auth.refresh(request.refresh_token())); }
    @PostMapping("/logout")
    public Envelope<Void> logout(@Valid @RequestBody Refresh request) { auth.logout(request.refresh_token()); return Envelope.ok(200,null); }
    @PostMapping("/logout-all")
    public Envelope<Void> logoutAll(@AuthenticationPrincipal IdentityPrincipal p) { auth.logoutAll(p.userId()); return Envelope.ok(200,null); }
}
