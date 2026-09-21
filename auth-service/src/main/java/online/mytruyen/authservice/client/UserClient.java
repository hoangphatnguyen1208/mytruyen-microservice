package online.mytruyen.authservice.client;

import online.mytruyen.authservice.common.Response;
import online.mytruyen.authservice.dto.UserInternal;
import online.mytruyen.authservice.dto.UserPublic;
import online.mytruyen.authservice.dto.UserRegister;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "user-service",
        url = "${user.service.url}",
        configuration = UserClientConfiguration.class
)
public interface UserClient {
    @GetMapping("/api/internal/users/by-username/{username}")
    Response<UserInternal> getUserByUsername(@PathVariable String username);

    @GetMapping("/api/internal/users/by-id/{id}")
    Response<UserInternal> getUserById(@PathVariable String id);

    @GetMapping("/api/internal/users/by-email/{email}")
    Response<UserInternal> getUserByEmail(@PathVariable String email);

    @PostMapping("/api/internal/users/register")
    Response<UserPublic> register(@RequestBody UserRegister userRegister);
}
