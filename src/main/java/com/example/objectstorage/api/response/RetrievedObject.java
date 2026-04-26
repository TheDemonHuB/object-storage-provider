package com.example.objectstorage.api.response;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;

public record RetrievedObject(
        StorageProvider provider,
        String bucket,
        String key,
        InputStream content,
        String contentType,
        Map<String, String> metadata,
        long size
) implements AutoCloseable {
    public RetrievedObject {
        Objects.requireNonNull(provider, "provider must not be null");
        bucket = ValidationUtils.requireNonBlank(bucket, "bucket");
        key = ValidationUtils.requireNonBlank(key, "key");
        Objects.requireNonNull(content, "content must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        contentType = ValidationUtils.normalizeToNull(contentType);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public void close() {
        try {
            content.close();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to close content stream", ex);
        }
    }

    @Override
    public String toString() {
        return "RetrievedObject[provider=" + provider
                + ", bucket=" + bucket
                + ", key=" + key
                + ", content=stream"
                + ", contentType=" + contentType
                + ", metadata=" + metadata
                + ", size=" + size + "]";
    }
}
