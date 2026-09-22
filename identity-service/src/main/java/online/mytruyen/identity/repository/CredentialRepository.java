package online.mytruyen.identity.repository;

import online.mytruyen.identity.domain.CredentialEntity;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface CredentialRepository extends JpaRepository<CredentialEntity, UUID> {}
