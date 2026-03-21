package online.mytruyen.userservice.common;

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
}
