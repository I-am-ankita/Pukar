package in.pukar.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ApiResponse} wrapper used in all REST endpoints.
 */
@DisplayName("ApiResponse - Standard API Envelope")
class ApiResponseTest {

    @Test
    @DisplayName("success() should wrap data with success=true and no error")
    void success_wrapsDataCorrectly() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertTrue(response.isSuccess());
        assertEquals("ok", response.getData());
        assertNull(response.getError());
    }

    @Test
    @DisplayName("failure() should wrap error with success=false and no data")
    void failure_wrapsErrorCorrectly() {
        ApiError error = new ApiError("TEST_ERROR", "Something went wrong");
        ApiResponse<String> response = ApiResponse.failure(error);

        assertFalse(response.isSuccess());
        assertNull(response.getData());
        assertNotNull(response.getError());
        assertEquals("TEST_ERROR", response.getError().getCode());
    }
}
