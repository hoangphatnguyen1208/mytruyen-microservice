package online.mytruyen.userservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mytruyen.userservice.entity.RoleEntity;
import online.mytruyen.userservice.entity.UserEntity;
import online.mytruyen.userservice.exception.RoleNotFoundException;
import online.mytruyen.userservice.repository.RoleRepository;
import online.mytruyen.userservice.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InitialDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String @NonNull ... args) {
        if (roleRepository.count() == 0) {
            roleRepository.save(RoleEntity.builder().name("ROLE_ADMIN").build());
            roleRepository.save(RoleEntity.builder().name("ROLE_USER").build());
             log.info(">>> Created default roles: ROLE_ADMIN, ROLE_USER");
        }

        if (userRepository.findByUsername("admin").isEmpty()) {
            RoleEntity adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseThrow(() -> new RoleNotFoundException("Role not found with name: ROLE_ADMIN"));

            UserEntity admin = new UserEntity();
            admin.setUsername("admin");
            admin.setHashed_password(passwordEncoder.encode("admin123"));
            admin.setRoles(List.of(adminRole));

            userRepository.save(admin);
            log.info(">>> Created default admin: admin/admin123");
        }
    }
}