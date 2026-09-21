package online.mytruyen.userservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mytruyen.userservice.entity.RoleEntity;
import online.mytruyen.userservice.entity.UserEntity;
import online.mytruyen.userservice.repository.RoleRepository;
import online.mytruyen.userservice.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class InitialDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String username;

    @Value("${app.admin.email}")
    private String email;

    @Value("${app.admin.password}")
    private String password;

    @Override
    @Transactional
    public void run(String @NonNull ... args) {
        RoleEntity adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().name("ROLE_ADMIN").build()));
        roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().name("ROLE_USER").build()));

        if (email.isBlank() || username.isBlank() || password.length() < 12) {
            throw new IllegalStateException("Bootstrap admin requires email, username, and a password of at least 12 characters");
        }

        if (userRepository.findByUsername(username).isEmpty()) {
            UserEntity admin = new UserEntity();
            admin.setEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
            admin.setUsername(username);
            admin.setHashed_password(passwordEncoder.encode(password));
            admin.setIs_active(true);
            admin.setRoles(List.of(adminRole));

            userRepository.save(admin);
            log.info(">>> Created default admin successfully");
        }
    }
}
