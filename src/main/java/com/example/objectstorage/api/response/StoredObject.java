package com.example.objectstorage.api.response;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;
import java.util.Objects;

public record StoredObject(
        StorageProvider provider,
        String bucket,
        String originalKey,
        String key,
        String originalFilename,
        String storedFilename,
        String eTag,
        String versionId
) {
    public StoredObject {
        Objects.requireNonNull(provider, "provider must not be null");
        bucket = ValidationUtils.requireNonBlank(bucket, "bucket");
        originalKey = ValidationUtils.requireNonBlank(originalKey, "originalKey");
        key = ValidationUtils.requireNonBlank(key, "key");
        originalFilename = ValidationUtils.requireNonBlank(originalFilename, "originalFilename");
        storedFilename = ValidationUtils.requireNonBlank(storedFilename, "storedFilename");
        eTag = ValidationUtils.normalizeToNull(eTag);
        versionId = ValidationUtils.normalizeToNull(versionId);
    }

    public StoredObject(StorageProvider provider, String bucket, String key, String eTag, String versionId) {
        this(
                provider,
                bucket,
                key,
                key,
                extractFilename(key),
                extractFilename(key),
                eTag,
                versionId
        );
    }

    private static String extractFilename(String key) {
        String normalizedKey = ValidationUtils.requireNonBlank(key, "key");
        int lastSlash = normalizedKey.lastIndexOf('/');
        return lastSlash >= 0 ? normalizedKey.substring(lastSlash + 1) : normalizedKey;
    }
}
