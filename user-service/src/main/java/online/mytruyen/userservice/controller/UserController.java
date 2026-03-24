package online.mytruyen.userservice.controller;

import lombok.AllArgsConstructor;
import online.mytruyen.userservice.service.UserService;
import online.mytruyen.userservice.common.PageResponse;
import online.mytruyen.userservice.common.Pagination;
import online.mytruyen.userservice.common.Response;
import online.mytruyen.userservice.entity.UserEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;


@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public PageResponse<List<UserEntity>> getUsers(Pageable pageable) {
        Page<UserEntity> pageData = userService.getUsers(pageable);
        Pagination pagination = new Pagination(pageData.getNumber(), pageData.getSize(), pageData.getTotalElements(), pageData.getTotalPages());
        return PageResponse.success(200, pageData.getContent(), pagination);
    }

    @GetMapping("/me")
    public Response<UserEntity> getCurrentUser() {

    }
}
