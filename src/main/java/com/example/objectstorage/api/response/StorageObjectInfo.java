package com.example.objectstorage.api.response;

import com.example.objectstorage.core.ValidationUtils;
import java.time.Instant;

public record StorageObjectInfo(String filePath, long size, Instant lastModified, String versionId) {
    public StorageObjectInfo {
        filePath = ValidationUtils.requireNonBlank(filePath, "filePath");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        versionId = ValidationUtils.normalizeToNull(versionId);
    }

    public StorageObjectInfo(String filePath, long size, Instant lastModified) {
        this(filePath, size, lastModified, null);
    }
}
