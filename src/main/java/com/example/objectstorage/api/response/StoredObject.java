package com.example.objectstorage.api.response;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;
import java.util.Objects;

public record StoredObject(
        StorageProvider provider,
        String bucket,
        String originalFilePath,
        String filePath,
        String originalFilename,
        String storedFilename,
        String eTag,
        String versionId
) {
    public StoredObject {
        Objects.requireNonNull(provider, "provider must not be null");
        bucket = ValidationUtils.requireNonBlank(bucket, "bucket");
        originalFilePath = ValidationUtils.requireNonBlank(originalFilePath, "originalFilePath");
        filePath = ValidationUtils.requireNonBlank(filePath, "filePath");
        originalFilename = ValidationUtils.requireNonBlank(originalFilename, "originalFilename");
        storedFilename = ValidationUtils.requireNonBlank(storedFilename, "storedFilename");
        eTag = ValidationUtils.normalizeToNull(eTag);
        versionId = ValidationUtils.normalizeToNull(versionId);
    }

    public StoredObject(StorageProvider provider, String bucket, String filePath, String eTag, String versionId) {
        this(
                provider,
                bucket,
                filePath,
                filePath,
                extractFilename(filePath),
                extractFilename(filePath),
                eTag,
                versionId
        );
    }

    private static String extractFilename(String filePath) {
        String normalizedFilePath = ValidationUtils.requireNonBlank(filePath, "filePath");
        int lastSlash = normalizedFilePath.lastIndexOf('/');
        return lastSlash >= 0 ? normalizedFilePath.substring(lastSlash + 1) : normalizedFilePath;
    }
}
