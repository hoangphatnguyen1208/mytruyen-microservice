package online.mytruyen.catalog.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class LegacyWorkerDtos {
    private LegacyWorkerDtos() {}
    public record BookInput(
        @NotNull @Positive Long id,
        @NotBlank @Size(max=500) String name,
        @NotBlank @Size(max=500) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        UUID author_id, @NotNull @Positive Long status_id,
        @NotNull Integer kind, @NotNull Integer sex, @NotNull String synopsis,
        Map<String,Object> poster, String note,
        @NotNull @PositiveOrZero Integer chapter_per_week, @NotNull Boolean published,
        @NotNull @Size(max=100) List<@NotNull @Positive Long> genre_ids,
        @NotNull @Size(max=100) List<@NotNull @Positive Long> tag_ids,
        @NotNull @PositiveOrZero Long chapter_count, @NotNull @PositiveOrZero Long word_count,
        @NotNull @PositiveOrZero Long view_count, @NotNull @PositiveOrZero Long comment_count,
        @NotNull @PositiveOrZero Long review_count, @NotNull @PositiveOrZero Double average_rating,
        @NotNull @PositiveOrZero Long bookmark_count) {
        public CatalogDtos.BookWrite asBook(long status) {
            return new CatalogDtos.BookWrite(name,slug,author_id,status,kind,sex,synopsis,poster,note,
                chapter_per_week,published,genre_ids,tag_ids);
        }
    }
    public record ChapterInput(@NotNull @Positive Integer index,@NotBlank @Size(max=500) String name,
        @NotNull @PositiveOrZero Long word_count,@NotNull Boolean published) {}
    public record BindInput(@NotNull @Positive Long book_id,@NotNull @Positive Long source_status_id) {}
}
