package online.mytruyen.catalog.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ChapterDtos {
    private ChapterDtos() {}
    public record Write(@NotNull @Positive Integer index, @NotBlank @Size(max=500) String name,
                        @AssertFalse Boolean published) {}
    public record ContentWrite(@NotBlank @Size(max=1000000) String content) {}
    public record MetadataWrite(@NotNull @Positive Integer index, @NotBlank @Size(max=500) String name) {}
    public record View(Long id, Long book_id, UUID creator_id, int index, String name, long word_count,
                       boolean published, Instant published_at, Instant created_at, Instant updated_at, long version) {}
    public record ContentView(Long chapter_id, String content, String content_hash,
                              Instant created_at, Instant updated_at, long version) {}
}
