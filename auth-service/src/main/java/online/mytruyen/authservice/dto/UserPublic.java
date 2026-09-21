package online.mytruyen.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

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
}
