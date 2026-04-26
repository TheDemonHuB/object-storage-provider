package com.example.objectstorage.api.response;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.objectstorage.api.StorageProvider;
import org.junit.jupiter.api.Test;

class StoredObjectTest {
    @Test
    void shouldNormalizeOptionalFieldsToNull() {
        StoredObject object = new StoredObject(StorageProvider.S3, "bucket", "key", "   ", "   ");

        assertNull(object.eTag());
        assertNull(object.versionId());
    }

    @Test
    void shouldPopulateOriginalAndStoredMetadataForConvenienceConstructor() {
        StoredObject object = new StoredObject(StorageProvider.S3, "bucket", "docs/file.txt", "etag", "v1");

        assertEquals("docs/file.txt", object.originalKey());
        assertEquals("docs/file.txt", object.key());
        assertEquals("file.txt", object.originalFilename());
        assertEquals("file.txt", object.storedFilename());
    }

    @Test
    void shouldRejectNullProvider() {
        assertThrows(NullPointerException.class, () -> new StoredObject(null, "bucket", "key", null, null));
    }
}
