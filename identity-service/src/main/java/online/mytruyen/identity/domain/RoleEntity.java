package online.mytruyen.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "IdentityRole")
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class RoleEntity {
    @Id
    private Short id;
    @Column(nullable = false, length = 32)
    private String code;
}
