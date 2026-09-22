package online.mytruyen.identity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.*;

@Entity(name = "IdentityRole")
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor
public class RoleEntity {
    @Id private Short id;
    @Column(nullable = false, length = 32) private String code;
}
