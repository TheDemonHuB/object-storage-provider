package com.example.objectstorage.api.response;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;
import java.util.Objects;

public record DeletedObject(
        StorageProvider provider,
        String bucket,
        String filePath,
        String versionId
) {
    public DeletedObject {
        Objects.requireNonNull(provider, "provider must not be null");
        bucket = ValidationUtils.requireNonBlank(bucket, "bucket");
        filePath = ValidationUtils.requireNonBlank(filePath, "filePath");
        versionId = ValidationUtils.normalizeToNull(versionId);
    }

    public DeletedObject(StorageProvider provider, String bucket, String filePath) {
        this(provider, bucket, filePath, null);
    }
}
