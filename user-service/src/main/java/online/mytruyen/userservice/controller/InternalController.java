package online.mytruyen.userservice.controller;

import lombok.AllArgsConstructor;
import online.mytruyen.userservice.entity.UserEntity;
import online.mytruyen.userservice.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@AllArgsConstructor
public class InternalController {
    private final UserRepository userRepository;

    @GetMapping("/by-username/{username}")
    public UserEntity getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}
