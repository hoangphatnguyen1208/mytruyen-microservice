package online.mytruyen.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="import_sources") @Getter @Setter
public class ImportSource {
    @Id @Column(length=50) private String code;
    @Column(nullable=false) private boolean enabled;
}
