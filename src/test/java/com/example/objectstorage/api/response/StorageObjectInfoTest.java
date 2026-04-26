package com.example.objectstorage.api.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StorageObjectInfoTest {
    @Test
    void shouldNormalizeOptionalVersionId() {
        StorageObjectInfo info = new StorageObjectInfo("docs/a.txt", 10L, Instant.parse("2026-01-01T00:00:00Z"), " ");

        assertNull(info.versionId());
    }

    @Test
    void shouldRejectNegativeSize() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new StorageObjectInfo("docs/a.txt", -1L, Instant.parse("2026-01-01T00:00:00Z"), null)
        );

        assertEquals("size must not be negative", ex.getMessage());
    }
}
