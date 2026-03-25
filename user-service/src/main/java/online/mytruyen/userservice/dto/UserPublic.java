package online.mytruyen.userservice.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;
import online.mytruyen.userservice.entity.UserEntity;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class UserPublic extends UserBase {
    private String id;

    private String email;

    private LocalDateTime created_at;

    private LocalDateTime updated_at;

    public UserPublic(UserEntity userEntity) {
        super(userEntity);
        this.id = userEntity.getId();
        this.email = userEntity.getEmail();
        this.created_at = userEntity.getCreated_at();
        this.updated_at = userEntity.getUpdated_at();
    }
}
