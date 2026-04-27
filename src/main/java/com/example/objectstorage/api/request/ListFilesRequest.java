package com.example.objectstorage.api.request;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.ValidationUtils;

public record ListFilesRequest(String prefix, Integer maxResults, StorageProvider provider, String bucket) {
    public ListFilesRequest {
        prefix = ValidationUtils.normalizeToNull(prefix);
        bucket = ValidationUtils.normalizeToNull(bucket);
        if (maxResults != null && maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be greater than 0");
        }
    }

    public ListFilesRequest(String prefix, Integer maxResults) {
        this(prefix, maxResults, null, null);
    }
}
