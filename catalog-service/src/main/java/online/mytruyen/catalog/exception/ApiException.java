package online.mytruyen.catalog.exception;
public class ApiException extends RuntimeException {
    public final int status;
    public ApiException(int status, String message) { super(message); this.status = status; }
    public static ApiException missing() { return new ApiException(404, "Resource not found"); }
    public static ApiException missing(String message) { return new ApiException(404, message); }
}
