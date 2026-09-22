package online.mytruyen.catalog.dto;

import online.mytruyen.catalog.exception.ApiException;

import java.util.List;
public final class ApiResponses {
    private ApiResponses() {}
    public record Response<T>(int status_code, boolean success, String message, T data) {
        public static <T> Response<T> ok(T value) { return new Response<>(200, true, "Success", value); }
        public static <T> Response<T> created(T value) { return new Response<>(201, true, "Created", value); }
    }
    public record Pagination(int page, int size, long total_items, long total_pages) {}
    public record Page<T>(int status_code, boolean success, String message, List<T> data, Pagination pagination) {}
    public static void validatePage(int page, int limit) {
        if (page < 1 || limit < 1 || limit > 100) throw new ApiException(400, "page must be >= 1 and limit between 1 and 100");
    }
}
