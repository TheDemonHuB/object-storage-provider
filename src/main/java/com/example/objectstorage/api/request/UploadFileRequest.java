package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

public record UploadFileRequest(
        String key,
        InputStream content,
        long contentLength,
        String contentType,
        Map<String, String> metadata,
        StorageProvider provider,
        String bucket
) {
    public UploadFileRequest {
        key = ValidationUtils.requireNonBlank(key, "key");
        Objects.requireNonNull(content, "content must not be null");
        if (contentLength <= 0) {
            throw new IllegalArgumentException("contentLength must be greater than 0");
        }
        contentType = ValidationUtils.normalizeToNull(contentType);
        bucket = ValidationUtils.normalizeToNull(bucket);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public UploadFileRequest(
            String key,
            InputStream content,
            long contentLength,
            String contentType,
            Map<String, String> metadata
    ) {
        this(key, content, contentLength, contentType, metadata, null, null);
    }

    @Override
    public String toString() {
        return "UploadFileRequest[key=" + key
                + ", content=stream"
                + ", contentLength=" + contentLength
                + ", contentType=" + contentType
                + ", metadata=" + metadata
                + ", provider=" + provider
                + ", bucket=" + bucket + "]";
    }
}
