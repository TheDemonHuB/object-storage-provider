package com.example.objectstorage.api.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                Map.of("owner", "team-a"),
                "v1",
                null,
                null
        );

        assertEquals("docs/sample.txt", request.filePath());
        assertEquals(3L, request.contentLength());
        assertEquals("text/plain", request.contentType());
        assertEquals("team-a", request.metadata().get("owner"));
        assertEquals("v1", request.versionId());
    }

    @Test
    void shouldNormalizeNullableMetadataAndBucket() {
        UploadFileRequest request = new UploadFileRequest(
                "docs/sample.txt",
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                " ",
                null,
                " ",
                null,
                " "
        );

        assertTrue(request.metadata().isEmpty());
        assertNull(request.contentType());
        assertNull(request.versionId());
        assertNull(request.bucket());
    }

    @Test
    void shouldRejectInvalidStreamUploadRequest() {
        assertThrows(IllegalArgumentException.class, () ->
                new UploadFileRequest(" ", new ByteArrayInputStream(new byte[]{1}), 1L, "text/plain", Map.of(), null, null, null));
        assertThrows(NullPointerException.class, () ->
                new UploadFileRequest("key", null, 1L, "text/plain", Map.of(), null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new UploadFileRequest("key", new ByteArrayInputStream(new byte[]{1}), 0L, "text/plain", Map.of(), null, null, null));
    }
}

