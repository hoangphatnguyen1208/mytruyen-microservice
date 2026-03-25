package online.mytruyen.userservice.common;

import org.springframework.data.domain.Page;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Pagination {
    private int page;
    private int limit;
    private Long total_items;
    private int total_pages;

    public Pagination(Page<?> page) {
        this.page = page.getNumber();
        this.limit = page.getSize();
        this.total_items = page.getTotalElements();
        this.total_pages = page.getTotalPages();
    }
}
