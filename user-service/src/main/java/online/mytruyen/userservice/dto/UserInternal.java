package online.mytruyen.userservice.dto;

import lombok.Builder;
import lombok.Data;
import online.mytruyen.userservice.entity.RoleEntity;
import online.mytruyen.userservice.entity.UserEntity;

import java.util.List;

@Data
@Builder
public class UserInternal {
    private String id;
    private String username;
    private String hashedPassword;
    private List<String> roles;

    public UserInternal(UserEntity userEntity) {
        this.id = userEntity.getId();
        this.username = userEntity.getUsername();
        this.hashedPassword = userEntity.getHashed_password();
        this.roles = userEntity.getRoles().stream().map(RoleEntity::getName).toList();
    }
}
