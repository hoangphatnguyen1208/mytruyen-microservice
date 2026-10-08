package online.mytruyen.catalog.repository;

import online.mytruyen.catalog.domain.CatalogOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CatalogOutboxRepository extends JpaRepository<CatalogOutboxEvent,UUID> {}
