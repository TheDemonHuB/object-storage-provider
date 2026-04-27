package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

public record UploadFileRequest(
        String filePath,
        InputStream content,
        long contentLength,
        String contentType,
        Map<String, String> metadata,
        String versionId,
        StorageProvider provider,
        String bucket
) {
    public UploadFileRequest {
        filePath = ValidationUtils.requireNonBlank(filePath, "filePath");
        Objects.requireNonNull(content, "content must not be null");
        if (contentLength <= 0) {
            throw new IllegalArgumentException("contentLength must be greater than 0");
        }
        contentType = ValidationUtils.normalizeToNull(contentType);
        versionId = ValidationUtils.normalizeToNull(versionId);
        bucket = ValidationUtils.normalizeToNull(bucket);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public String toString() {
        return "UploadFileRequest[filePath=" + filePath
                + ", content=stream"
                + ", contentLength=" + contentLength
                + ", contentType=" + contentType
                + ", metadata=" + metadata
                + ", versionId=" + versionId
                + ", provider=" + provider
                + ", bucket=" + bucket + "]";
    }
}
