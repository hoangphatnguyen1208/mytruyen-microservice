package online.mytruyen.identity.repository;

import online.mytruyen.identity.domain.CredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CredentialRepository extends JpaRepository<CredentialEntity, UUID> {
}
