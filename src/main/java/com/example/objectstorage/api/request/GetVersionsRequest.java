package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;

public record GetVersionsRequest(String filePath, StorageProvider provider, String bucket) {
    public GetVersionsRequest {
        filePath = ValidationUtils.requireNonBlank(filePath, "filePath");
        bucket = ValidationUtils.normalizeToNull(bucket);
    }
}

