package online.mytruyen.userservice.controller;

import lombok.AllArgsConstructor;
import online.mytruyen.userservice.common.Response;
import online.mytruyen.userservice.dto.UserInternal;
import online.mytruyen.userservice.entity.UserEntity;
import online.mytruyen.userservice.exception.UserNotFoundException;
import online.mytruyen.userservice.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/users")
@AllArgsConstructor
public class InternalController {
    private final UserRepository userRepository;

    @GetMapping("/by-username/{username}")
    public ResponseEntity<Response<UserInternal>> getUserByUsername(@PathVariable String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(Response.success(200, new UserInternal(user)));
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<Response<UserInternal>> getUserById(@PathVariable String id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(Response.success(200, new UserInternal(user)));
    }

    @GetMapping("/by-email/{email}")
    public ResponseEntity<Response<UserInternal>> getUserByEmail(@PathVariable String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(Response.success(200, new UserInternal(user)));
    }
}
