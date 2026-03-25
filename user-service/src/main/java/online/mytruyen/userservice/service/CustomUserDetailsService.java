package online.mytruyen.userservice.service;

import lombok.AllArgsConstructor;
import online.mytruyen.userservice.entity.RoleEntity;
import online.mytruyen.userservice.entity.UserEntity;
import online.mytruyen.userservice.repository.UserRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@NullMarked
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        return User.builder()
                .username(user.getUsername())
                .password(user.getHashed_password())
                .authorities(user.getRoles().stream().map(RoleEntity::getName).toArray(String[]::new))
                .build();
    }
}
