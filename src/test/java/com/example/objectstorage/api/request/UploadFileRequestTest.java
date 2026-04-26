package com.example.objectstorage.api.request;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UploadFileRequestTest {
    @Test
    void shouldDefensivelyCopyInputContent() {
        byte[] original = new byte[]{1, 2, 3};
        UploadFileRequest request = new UploadFileRequest(
                "test-key",
                original,
                "application/octet-stream",
                Map.of()
        );

        original[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, request.content());
    }

    @Test
    void shouldRejectEmptyPayload() {
        String key = "test-key";
        byte[] emptyPayload = new byte[0];
        String contentType = "application/octet-stream";
        Map<String, String> metadata = Map.of();

        assertThrows(IllegalArgumentException.class, () -> new UploadFileRequest(
                key,
                emptyPayload,
                contentType,
                metadata
        ));
    }

    @Test
    void shouldUseContentBasedEqualityAndHashCode() {
        UploadFileRequest expected = new UploadFileRequest(
                "key",
                new byte[]{7, 8, 9},
                "text/plain",
                Map.of("owner", "team-a")
        );
        UploadFileRequest actual = new UploadFileRequest(
                "key",
                new byte[]{7, 8, 9},
                "text/plain",
                Map.of("owner", "team-a")
        );

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
        assertEquals(expected, expected);
        assertNotEquals("different-type", expected);
        assertTrue(expected.toString().contains("content=byte[3]"));
    }

    @Test
    void shouldReturnFalseWhenAnySignificantFieldDiffers() {
        UploadFileRequest base = new UploadFileRequest(
                "key",
                new byte[]{7, 8, 9},
                "text/plain",
                Map.of("owner", "team-a")
        );

        assertNotEquals(base, new UploadFileRequest(
                "key-2",
                new byte[]{7, 8, 9},
                "text/plain",
                Map.of("owner", "team-a")
        ));
        assertNotEquals(base, new UploadFileRequest(
                "key",
                new byte[]{1, 2, 3},
                "text/plain",
                Map.of("owner", "team-a")
        ));
        assertNotEquals(base, new UploadFileRequest(
                "key",
                new byte[]{7, 8, 9},
                "application/json",
                Map.of("owner", "team-a")
        ));
        assertNotEquals(base, new UploadFileRequest(
                "key",
                new byte[]{7, 8, 9},
                "text/plain",
                Map.of("owner", "team-b")
        ));
    }
}
