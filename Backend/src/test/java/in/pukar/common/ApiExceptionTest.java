package in.pukar.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ApiException} factory methods.
 * Ensures API errors carry correct HTTP status and error codes.
 */
@DisplayName("ApiException - Custom API Error Handling")
class ApiExceptionTest {

    @Test
    @DisplayName("notFound() should return 404 with given code and message")
    void notFound_returns404() {
        ApiException ex = ApiException.notFound("COMPLAINT_NOT_FOUND", "Complaint not found");

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("COMPLAINT_NOT_FOUND", ex.getCode());
        assertEquals("Complaint not found", ex.getMessage());
    }

    @Test
    @DisplayName("badRequest() should return 400 with given code and message")
    void badRequest_returns400() {
        ApiException ex = ApiException.badRequest("OUTSIDE_SERVICE_AREA", "Outside Mumbai limits");

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("OUTSIDE_SERVICE_AREA", ex.getCode());
    }

    @Test
    @DisplayName("forbidden() should return 403 with given code and message")
    void forbidden_returns403() {
        ApiException ex = ApiException.forbidden("NOT_REPORTER", "Only reporter can give feedback");

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("NOT_REPORTER", ex.getCode());
    }

    @Test
    @DisplayName("unauthorized() should return 401 with given code and message")
    void unauthorized_returns401() {
        ApiException ex = ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials");

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    @DisplayName("conflict() should return 409 with given code and message")
    void conflict_returns409() {
        ApiException ex = ApiException.conflict("DUPLICATE", "Already exists");

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("DUPLICATE", ex.getCode());
    }
}
