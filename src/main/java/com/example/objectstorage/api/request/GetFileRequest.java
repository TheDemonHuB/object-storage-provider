package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;

public record GetFileRequest(String key, StorageProvider provider, String bucket) {
    public GetFileRequest {
        key = ValidationUtils.requireNonBlank(key, "key");
        bucket = ValidationUtils.normalizeToNull(bucket);
    }

    public GetFileRequest(String key) {
        this(key, null, null);
    }
}
