package online.mytruyen.userservice.service;

import lombok.AllArgsConstructor;
import online.mytruyen.userservice.dto.UserPublic;
import online.mytruyen.userservice.dto.UserCreate;
import online.mytruyen.userservice.dto.UserUpdate;
import online.mytruyen.userservice.entity.RoleEntity;
import online.mytruyen.userservice.entity.UserEntity;
import online.mytruyen.userservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;

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
        UserEntity user = userRepository.findByUsername(username);
        return new UserPublic(user);
    }

    public UserPublic save(UserCreate userCreate) {
        List<RoleEntity> roles = userCreate.getRoles().stream().map(roleService::getRoleById).toList();

        UserEntity user = new UserEntity();
        user.setUsername(userCreate.getUsername());
        user.setHashed_password(passwordEncoder.encode(userCreate.getPassword()));
        user.setRoles(roles);

        UserEntity userDb = userRepository.save(user);
        return new UserPublic(userDb);
    }

    public UserPublic updateMe(UserUpdate userUpdate, UserDetails userDetails) {
        UserEntity user = userRepository.findByUsername(userDetails.getUsername());

        if (user.getFull_name() != null) {
            user.setFull_name(userUpdate.getFull_name());
        }

        if (userRepository.findByUsername(userUpdate.getUsername()) == null) {
            user.setUsername(userUpdate.getUsername());
        }

        UserEntity user_updated = userRepository.save(user);
        return new UserPublic(user_updated);
    }

    public void deleteMe(UserDetails userDetails) {
        UserEntity user = userRepository.findByUsername(userDetails.getUsername());
        userRepository.delete(user);
    }
}
