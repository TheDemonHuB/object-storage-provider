package com.example.objectstorage.config;

import com.example.objectstorage.core.ValidationUtils;

public record S3StorageConfig(
        String region,
        String accessKey,
        String secretKey,
        String endpointOverride,
        boolean pathStyleAccessEnabled
) {
    public S3StorageConfig {
        region = ValidationUtils.requireNonBlank(region, "region");
        accessKey = ValidationUtils.requireNonBlank(accessKey, "accessKey");
        secretKey = ValidationUtils.requireNonBlank(secretKey, "secretKey");
        endpointOverride = ValidationUtils.normalizeToNull(endpointOverride);
    }
}
