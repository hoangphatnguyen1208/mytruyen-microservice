package online.mytruyen.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.mytruyen.userservice.entity.UserEntity;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class UserBase {
    private String username;

    private String full_name;

    public UserBase(UserEntity userEntity) {
        this.username = userEntity.getUsername();
        this.full_name = userEntity.getFull_name();
    }
}
