package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public record UploadFileRequest(
        String key,
        byte[] content,
        String contentType,
        Map<String, String> metadata,
        StorageProvider provider,
        String bucket
) {
    public UploadFileRequest {
        key = ValidationUtils.requireNonBlank(key, "key");
        Objects.requireNonNull(content, "content must not be null");
        if (content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        contentType = ValidationUtils.normalizeToNull(contentType);
        bucket = ValidationUtils.normalizeToNull(bucket);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        content = Arrays.copyOf(content, content.length);
    }

    public UploadFileRequest(
            String key,
            byte[] content,
            String contentType,
            Map<String, String> metadata
    ) {
        this(key, content, contentType, metadata, null, null);
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
        if (!(other instanceof UploadFileRequest that)) {
            return false;
        }
        return Objects.equals(key, that.key)
                && Arrays.equals(content, that.content)
                && Objects.equals(contentType, that.contentType)
                && Objects.equals(metadata, that.metadata)
                && provider == that.provider
                && Objects.equals(bucket, that.bucket);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(key, contentType, metadata, provider, bucket);
        return 31 * result + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "UploadFileRequest[key=" + key
                + ", content=byte[" + content.length + "]"
                + ", contentType=" + contentType
                + ", metadata=" + metadata
                + ", provider=" + provider
                + ", bucket=" + bucket + "]";
    }
}
