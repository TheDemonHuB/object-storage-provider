package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;

public record CopyFileRequest(
        String sourceKey,
        String sourceVersionId,
        String targetKey,
        StorageProvider sourceProvider,
        String sourceBucket,
        StorageProvider targetProvider,
        String targetBucket
) {
    public CopyFileRequest {
        sourceKey = ValidationUtils.requireNonBlank(sourceKey, "sourceKey");
        sourceVersionId = ValidationUtils.normalizeToNull(sourceVersionId);
        targetKey = ValidationUtils.requireNonBlank(targetKey, "targetKey");
        sourceBucket = ValidationUtils.normalizeToNull(sourceBucket);
        targetBucket = ValidationUtils.normalizeToNull(targetBucket);
    }

}
