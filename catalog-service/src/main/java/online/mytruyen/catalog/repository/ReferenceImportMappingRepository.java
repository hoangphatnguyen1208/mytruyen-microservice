package online.mytruyen.catalog.repository;

import online.mytruyen.catalog.domain.ReferenceImportMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReferenceImportMappingRepository extends JpaRepository<ReferenceImportMapping,UUID> {
    Optional<ReferenceImportMapping> findBySourceAndKindAndExternalId(String source,String kind,String externalId);
}
