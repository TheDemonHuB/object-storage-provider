package com.example.objectstorage.api.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UploadFileRequestTest {
    @Test
    void shouldCreateUploadRequestWithStreamContent() {
        UploadFileRequest request = new UploadFileRequest(
                "docs/sample.txt",
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3L,
                "text/plain",
                Map.of("owner", "team-a")
        );

        assertEquals("docs/sample.txt", request.key());
        assertEquals(3L, request.contentLength());
        assertEquals("text/plain", request.contentType());
        assertEquals("team-a", request.metadata().get("owner"));
    }

    @Test
    void shouldRejectInvalidStreamUploadRequest() {
        assertThrows(IllegalArgumentException.class, () ->
                new UploadFileRequest(" ", new ByteArrayInputStream(new byte[]{1}), 1L, "text/plain", Map.of()));
        assertThrows(NullPointerException.class, () ->
                new UploadFileRequest("key", null, 1L, "text/plain", Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new UploadFileRequest("key", new ByteArrayInputStream(new byte[]{1}), 0L, "text/plain", Map.of()));
    }
}
