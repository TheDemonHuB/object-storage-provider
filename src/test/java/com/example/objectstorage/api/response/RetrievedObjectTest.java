package com.example.objectstorage.api.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.objectstorage.api.StorageProvider;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievedObjectTest {
    @Test
    void shouldCreateAndCloseRetrievedObject() {
        RetrievedObject object = new RetrievedObject(
                StorageProvider.S3,
                "bucket",
                "key",
                null,
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                "application/octet-stream",
                Map.of("a", "b"),
                3L
        );

        assertEquals(StorageProvider.S3, object.provider());
        assertEquals("bucket", object.bucket());
        assertEquals("key", object.filePath());
        assertEquals(3L, object.size());
        object.close();
    }

    @Test
    void shouldRejectInvalidRetrievedObject() {
        assertThrows(NullPointerException.class, () ->
                new RetrievedObject(null, "bucket", "key", null, new ByteArrayInputStream(new byte[]{1}), null, Map.of(), 1L));
        assertThrows(IllegalArgumentException.class, () ->
                new RetrievedObject(StorageProvider.S3, " ", "key", null, new ByteArrayInputStream(new byte[]{1}), null, Map.of(), 1L));
        assertThrows(IllegalArgumentException.class, () ->
                new RetrievedObject(StorageProvider.S3, "bucket", "key", null, new ByteArrayInputStream(new byte[]{1}), null, Map.of(), -1L));
    }
}

