package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;

public record CopyFileRequest(
        String sourceKey,
        String targetKey,
        StorageProvider sourceProvider,
        String sourceBucket,
        StorageProvider targetProvider,
        String targetBucket
) {
    public CopyFileRequest {
        sourceKey = ValidationUtils.requireNonBlank(sourceKey, "sourceKey");
        targetKey = ValidationUtils.requireNonBlank(targetKey, "targetKey");
        sourceBucket = ValidationUtils.normalizeToNull(sourceBucket);
        targetBucket = ValidationUtils.normalizeToNull(targetBucket);
    }

    public CopyFileRequest(String sourceKey, String targetKey) {
        this(sourceKey, targetKey, null, null, null, null);
    }
}
