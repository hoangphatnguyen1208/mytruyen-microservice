package online.mytruyen.authservice.service;

import lombok.RequiredArgsConstructor;
import online.mytruyen.authservice.client.UserClient;
import online.mytruyen.authservice.dto.UserInternal;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NullMarked
public class CustomUserDetailsService implements UserDetailsService {

    private final UserClient userClient;
    @Override
    public UserDetails loadUserByUsername(String id) {
        UserInternal user =  userClient.getUserByUsername(id).getData();
        return User.builder()
                .username(user.getUsername())
                .password(user.getHashedPassword())
                .authorities(user.getRoles().stream().map(role -> "ROLE_" + role).toArray(String[]::new))
                .build();
    }
}
