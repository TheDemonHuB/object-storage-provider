package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;

public record DeleteFileRequest(String key, StorageProvider provider, String bucket) {
    public DeleteFileRequest {
        key = ValidationUtils.requireNonBlank(key, "key");
        bucket = ValidationUtils.normalizeToNull(bucket);
    }

    public DeleteFileRequest(String key) {
        this(key, null, null);
    }
}
