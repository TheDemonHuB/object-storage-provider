package com.example.objectstorage.api.response;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.objectstorage.api.StorageProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievedObjectTest {
    @Test
    void shouldDefensivelyCopyContentAndUseContentBasedEquality() {
        byte[] firstBytes = new byte[]{1, 2, 3};
        RetrievedObject expected = new RetrievedObject(
                StorageProvider.S3,
                "bucket",
                "key",
                firstBytes,
                "text/plain",
                Map.of("owner", "team-a"),
                3
        );
        RetrievedObject actual = new RetrievedObject(
                StorageProvider.S3,
                "bucket",
                "key",
                new byte[]{1, 2, 3},
                "text/plain",
                Map.of("owner", "team-a"),
                3
        );

        firstBytes[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, expected.content());
        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
        assertEquals(expected, expected);
        assertNotEquals("different-type", expected);
        assertTrue(expected.toString().contains("content=byte[3]"));
        assertTrue(expected.toString().contains("size=3"));
    }

    @Test
    void shouldRejectNegativeSize() {
        StorageProvider provider = StorageProvider.S3;
        String bucket = "bucket";
        String key = "key";
        byte[] content = new byte[]{1};
        String contentType = null;
        Map<String, String> metadata = Map.of();
        long size = -1;

        assertThrows(IllegalArgumentException.class, () ->
                new RetrievedObject(provider, bucket, key, content, contentType, metadata, size));
    }

    @Test
    void shouldReturnFalseWhenAnySignificantFieldDiffers() {
        RetrievedObject base = new RetrievedObject(
                StorageProvider.S3,
                "bucket",
                "key",
                new byte[]{1, 2, 3},
                "text/plain",
                Map.of("owner", "team-a"),
                3
        );

        assertNotEquals(base, new RetrievedObject(
                StorageProvider.GCP,
                "bucket",
                "key",
                new byte[]{1, 2, 3},
                "text/plain",
                Map.of("owner", "team-a"),
                3
        ));
        assertNotEquals(base, new RetrievedObject(
                StorageProvider.S3,
                "bucket-2",
                "key",
                new byte[]{1, 2, 3},
                "text/plain",
                Map.of("owner", "team-a"),
                3
        ));
        assertNotEquals(base, new RetrievedObject(
                StorageProvider.S3,
                "bucket",
                "key-2",
                new byte[]{1, 2, 3},
                "text/plain",
                Map.of("owner", "team-a"),
                3
        ));
        assertNotEquals(base, new RetrievedObject(
                StorageProvider.S3,
                "bucket",
                "key",
                new byte[]{9, 8, 7},
                "text/plain",
                Map.of("owner", "team-a"),
                3
        ));
        assertNotEquals(base, new RetrievedObject(
                StorageProvider.S3,
                "bucket",
                "key",
                new byte[]{1, 2, 3},
                "application/json",
                Map.of("owner", "team-a"),
                3
        ));
        assertNotEquals(base, new RetrievedObject(
                StorageProvider.S3,
                "bucket",
                "key",
                new byte[]{1, 2, 3},
                "text/plain",
                Map.of("owner", "team-b"),
                3
        ));
        assertNotEquals(base, new RetrievedObject(
                StorageProvider.S3,
                "bucket",
                "key",
                new byte[]{1, 2, 3},
                "text/plain",
                Map.of("owner", "team-a"),
                4
        ));
    }
}
