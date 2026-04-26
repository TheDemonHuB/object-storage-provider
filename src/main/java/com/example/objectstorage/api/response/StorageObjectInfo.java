package com.example.objectstorage.api.response;

import com.example.objectstorage.core.ValidationUtils;
import java.time.Instant;

public record StorageObjectInfo(String key, long size, Instant lastModified) {
    public StorageObjectInfo {
        key = ValidationUtils.requireNonBlank(key, "key");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }
}
