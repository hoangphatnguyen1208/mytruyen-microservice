package online.mytruyen.catalog.repository;

import online.mytruyen.catalog.domain.BookImportMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface BookImportMappingRepository extends JpaRepository<BookImportMapping,UUID> {
    Optional<BookImportMapping> findBySourceAndExternalId(String source,String externalId);
}
