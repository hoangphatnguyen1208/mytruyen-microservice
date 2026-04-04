package online.mytruyen.authservice.service;

import lombok.RequiredArgsConstructor;
import online.mytruyen.authservice.client.UserClient;
import online.mytruyen.authservice.dto.UserInternal;
import online.mytruyen.authservice.dto.UserLogin;
import online.mytruyen.authservice.exception.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserClient userClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public String login(UserLogin userLogin) {
        UserInternal user =  userClient.getUserByEmail(userLogin.getEmail()).getData();

        if (user == null || !passwordEncoder.matches(userLogin.getPassword(), user.getHashedPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return jwtService.generateToken(user.getId(), user.getRoles());
    }
}
