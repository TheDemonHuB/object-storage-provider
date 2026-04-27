package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;

public record GetFileRequest(String filePath, String versionId, StorageProvider provider, String bucket) {
    public GetFileRequest {
        filePath = ValidationUtils.requireNonBlank(filePath, "filePath");
        versionId = ValidationUtils.normalizeToNull(versionId);
        bucket = ValidationUtils.normalizeToNull(bucket);
    }
}
