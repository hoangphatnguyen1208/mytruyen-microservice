package online.mytruyen.identity;

public class ApiError extends RuntimeException {
    final int status;
    public ApiError(int status, String message) { super(message); this.status = status; }
}
