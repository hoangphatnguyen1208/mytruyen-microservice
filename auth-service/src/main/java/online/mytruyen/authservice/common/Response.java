package online.mytruyen.authservice.common;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Response<T> {
    private int status_code;
    private boolean success;
    private String message;
    private T data;

    public static <T> Response<T> success(int status_code, T data) {
        return Response.<T>builder()
                .status_code(status_code)
                .success(true)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> Response<T> error(int status_code, String message) {
        return Response.<T>builder()
                .status_code(status_code)
                .success(false)
                .message(message)
                .data(null)
                .build();
    }
}
