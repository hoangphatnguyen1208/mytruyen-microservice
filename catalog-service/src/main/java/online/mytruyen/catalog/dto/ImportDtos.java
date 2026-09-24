package online.mytruyen.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public final class ImportDtos {
    private ImportDtos() {}
    // Reference IDs here are Catalog IDs, never upstream taxonomy/author IDs.
    public record Metadata(
        @NotBlank @Size(max=500) String name,
        @NotBlank @Size(max=500) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        UUID author_id, @NotNull @Positive Long status_id,
        @NotNull Integer kind, @NotNull Integer sex,
        @NotBlank @Size(max=100000) String synopsis,
        Map<String,Object> poster, @Size(max=10000) String note,
        @PositiveOrZero Integer chapter_per_week,
        @Size(max=100) List<@NotNull @Positive Long> genre_ids,
        @Size(max=100) List<@NotNull @Positive Long> tag_ids) {
        public CatalogDtos.BookWrite asBook() {
            return new CatalogDtos.BookWrite(name,slug,author_id,status_id,kind,sex,synopsis,
                poster,note,chapter_per_week,false,genre_ids,tag_ids);
        }
    }
    public record BookImport(@PositiveOrZero Long expected_version,
                             @NotNull @Valid Metadata metadata) {}
    public record Result(String source,String external_id,long book_id,long version,String outcome) {}
}
