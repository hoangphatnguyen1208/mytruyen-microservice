package online.mytruyen.catalog.mapper;

import online.mytruyen.catalog.domain.*;
import online.mytruyen.catalog.dto.ChapterDtos.*;

public final class ChapterViews {
    private ChapterViews() {}
    public static View chapter(Chapter c) {
        return new View(c.getId(),c.getBook().getId(),c.getCreatorId(),c.getChapterIndex(),c.getName(),
            c.getWordCount(),c.isPublished(),c.getPublishedAt(),c.getCreatedAt(),c.getUpdatedAt(),c.getVersion());
    }
    public static ContentView content(ChapterContent c) {
        return new ContentView(c.getChapterId(),c.getContent(),c.getContentHash(),
            c.getCreatedAt(),c.getUpdatedAt(),c.getVersion());
    }
}
