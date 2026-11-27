package pt.arrumo.errors;

import org.springframework.http.HttpStatus;

/** Error whose message is meant to be shown to the user (so it is in Portuguese). */
public class ApiError extends RuntimeException {
    private final HttpStatus status;

    public ApiError(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static ApiError notFound(String msg) { return new ApiError(HttpStatus.NOT_FOUND, msg); }
    public static ApiError conflict(String msg) { return new ApiError(HttpStatus.CONFLICT, msg); }
    public static ApiError invalid(String msg) { return new ApiError(HttpStatus.BAD_REQUEST, msg); }
    public static ApiError unauthenticated(String msg) { return new ApiError(HttpStatus.UNAUTHORIZED, msg); }
}
