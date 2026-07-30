package in.pukar.common;

import java.time.Instant;

public class ApiError {
    private String code;
    private String message;
    private String timestamp;

    public ApiError() {}

    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = Instant.now().toString();
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public void setCode(String code) { this.code = code; }
    public void setMessage(String message) { this.message = message; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
