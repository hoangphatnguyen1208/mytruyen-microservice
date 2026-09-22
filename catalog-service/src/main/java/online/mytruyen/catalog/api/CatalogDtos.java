package online.mytruyen.catalog.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class CatalogDtos {
    private CatalogDtos() {}
    public record BookWrite(
        @NotBlank @Size(max=500) String name,
        @NotBlank @Size(max=500) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        UUID author_id, @NotNull @Positive Long status_id,
        @NotNull Integer kind, @NotNull Integer sex,
        @NotBlank String synopsis, Map<String,Object> poster, String note,
        @PositiveOrZero Integer chapter_per_week, Boolean published,
        @Size(max=100) List<@NotNull @Positive Long> genre_ids,
        @Size(max=100) List<@NotNull @Positive Long> tag_ids) {}
    public record AuthorWrite(@NotBlank @Size(max=255) String name,
        @Size(max=255) String local_name, String avatar) {}
    public record TaxonWrite(@NotBlank @Size(max=150) String name,
        @NotBlank @Size(max=150) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        String description, @Size(max=50) String type) {}
    public record AuthorView(UUID id, String name, String local_name, String avatar, Instant created_at, Instant updated_at) {}
    public record TaxonView(Long id, String name, String slug, String description, String type, Instant created_at, Instant updated_at) {}
    public record BookView(Long id, UUID creator_id, String name, String slug, int kind, int sex,
        Long status_id, int chapter_per_week, boolean published, String synopsis, Map<String,Object> poster,
        String note, AuthorView author, TaxonView status, List<TaxonView> genres, List<TaxonView> tags,
        long chapter_count, long word_count, Integer latest_chapter, Instant new_chap_at,
        long view_count, long comment_count, long review_count, double average_rating, long bookmark_count,
        Instant created_at, Instant updated_at, Instant published_at, long version) {}
}
