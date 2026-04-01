package online.mytruyen.authservice.client;

import online.mytruyen.authservice.common.Response;
import online.mytruyen.authservice.dto.UserInternal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${user.service.url}")
public interface UserClient {
    @GetMapping("internal/users/by-username/{username}")
    Response<UserInternal> getUserByUsername(@PathVariable String username);

    @GetMapping("internal/users/by-id/{id}")
    Response<UserInternal> getUserById(@PathVariable String id);

    @GetMapping("/internal/users/by-email/{email}")
    Response<UserInternal> getUserByEmail(@PathVariable String email);
}
