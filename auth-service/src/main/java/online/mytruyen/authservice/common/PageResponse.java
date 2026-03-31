package online.mytruyen.authservice.common;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class PageResponse<T> extends Response<T>{
    private Pagination pagination;

    public static <T> PageResponse<T> success(int status_code, T data, Pagination pagination) {
        return PageResponse.<T>builder()
                .status_code(status_code)
                .status(true)
                .message("Success")
                .data(data)
                .pagination(pagination)
                .build();
    }

    public static <T> PageResponse<T> error(int status_code, String message) {
        return PageResponse.<T>builder()
                .status_code(status_code)
                .status(false)
                .message(message)
                .data(null)
                .pagination(null)
                .build();
    }
}
