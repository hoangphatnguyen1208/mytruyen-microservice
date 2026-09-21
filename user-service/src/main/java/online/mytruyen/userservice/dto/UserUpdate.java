package online.mytruyen.userservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import online.mytruyen.userservice.entity.UserEntity;

@Getter
@Setter
@RequiredArgsConstructor
public class UserUpdate extends UserBase{
    @Override
    @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,50}$")
    public void setUsername(String username) {
        super.setUsername(username);
    }

    @Override
    @Size(max = 100)
    public void setFull_name(String fullName) {
        super.setFull_name(fullName);
    }

    public UserUpdate(UserEntity userEntity) {
        super(userEntity);
    }
}
