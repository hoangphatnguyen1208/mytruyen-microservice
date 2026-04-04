package online.mytruyen.userservice.dto;

import lombok.*;
import online.mytruyen.userservice.entity.UserEntity;

@Getter
@Setter
@RequiredArgsConstructor
public class UserUpdate extends UserBase{
    public UserUpdate(UserEntity userEntity) {
        super(userEntity);
    }
}
