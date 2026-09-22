package online.mytruyen.identity;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface RoleRepository extends JpaRepository<RoleEntity, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdentityRole r where r.id = 2")
    RoleEntity lockAdminRole();
}
