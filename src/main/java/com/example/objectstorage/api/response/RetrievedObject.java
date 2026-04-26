package com.example.objectstorage.api.response;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public record RetrievedObject(
        StorageProvider provider,
        String bucket,
        String key,
        byte[] content,
        String contentType,
        Map<String, String> metadata,
        long size
) {
    public RetrievedObject {
        Objects.requireNonNull(provider, "provider must not be null");
        bucket = ValidationUtils.requireNonBlank(bucket, "bucket");
        key = ValidationUtils.requireNonBlank(key, "key");
        Objects.requireNonNull(content, "content must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        content = Arrays.copyOf(content, content.length);
        contentType = ValidationUtils.normalizeToNull(contentType);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RetrievedObject that)) {
            return false;
        }
        return size == that.size
                && provider == that.provider
                && Objects.equals(bucket, that.bucket)
                && Objects.equals(key, that.key)
                && Arrays.equals(content, that.content)
                && Objects.equals(contentType, that.contentType)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(provider, bucket, key, contentType, metadata, size);
        return 31 * result + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "RetrievedObject[provider=" + provider
                + ", bucket=" + bucket
                + ", key=" + key
                + ", content=byte[" + content.length + "]"
                + ", contentType=" + contentType
                + ", metadata=" + metadata
                + ", size=" + size + "]";
    }
}
