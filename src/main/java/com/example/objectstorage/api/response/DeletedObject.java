package com.example.objectstorage.api.response;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;
import java.util.Objects;

public record DeletedObject(
        StorageProvider provider,
        String bucket,
        String key
) {
    public DeletedObject {
        Objects.requireNonNull(provider, "provider must not be null");
        bucket = ValidationUtils.requireNonBlank(bucket, "bucket");
        key = ValidationUtils.requireNonBlank(key, "key");
    }
}
