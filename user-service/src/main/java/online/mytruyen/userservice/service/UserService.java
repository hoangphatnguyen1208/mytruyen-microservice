package online.mytruyen.userservice.service;

import lombok.AllArgsConstructor;
import online.mytruyen.userservice.dto.UserCreate;
import online.mytruyen.userservice.dto.UserPublic;
import online.mytruyen.userservice.dto.UserRegister;
import online.mytruyen.userservice.dto.UserUpdate;
import online.mytruyen.userservice.entity.RoleEntity;
import online.mytruyen.userservice.entity.UserEntity;
import online.mytruyen.userservice.exception.UserAlreadyExistsException;
import online.mytruyen.userservice.exception.UserNotFoundException;
import online.mytruyen.userservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    public Page<UserPublic> getUsers(Pageable pageable) {
        Page<UserEntity> page = userRepository.findAll(pageable);

        return page.map(UserPublic::new);
    }

    public UserPublic getUserByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return new UserPublic(user);
    }

    public UserPublic getUserById(String id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return new UserPublic(user);
    }

    public UserPublic save(UserCreate userCreate) {
        String email = normalizeEmail(userCreate.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        if (userRepository.existsByUsername(userCreate.getUsername())) {
            throw new UserAlreadyExistsException("Username already exists");
        }

        List<RoleEntity> roles = userCreate.getRoles().stream().map(roleService::getRoleById).toList();

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setUsername(userCreate.getUsername().trim());
        user.setHashed_password(passwordEncoder.encode(userCreate.getPassword()));
        user.setIs_active(true);
        user.setRoles(roles);

        UserEntity userDb = userRepository.save(user);
        return new UserPublic(userDb);
    }

    public UserPublic save(UserRegister userRegister) {
        String email = normalizeEmail(userRegister.getEmail());
        String username = userRegister.getUsername().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists");
        }

        RoleEntity roleUser = roleService.getRoleByName("ROLE_USER");

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setUsername(username);
        user.setHashed_password(passwordEncoder.encode(userRegister.getPassword()));
        user.setIs_active(true);
        user.setRoles(List.of(roleUser));

        UserEntity userDb = userRepository.save(user);
        return new UserPublic(userDb);
    }

    public UserPublic updateMe(UserUpdate userUpdate, UserDetails userDetails) {
        UserEntity user = userRepository.findById(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (userUpdate.getFull_name() != null) {
            user.setFull_name(userUpdate.getFull_name().trim());
        }

        if (userUpdate.getUsername() != null) {
            String username = userUpdate.getUsername().trim();
            if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
                throw new UserAlreadyExistsException("Username already exists");
            }
            user.setUsername(username);
        }

        UserEntity user_updated = userRepository.save(user);
        return new UserPublic(user_updated);
    }

    public void deleteMe(UserDetails userDetails) {
        UserEntity user = userRepository.findById(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        userRepository.delete(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
