package com.example.objectstorage.config;

import com.example.objectstorage.core.ValidationUtils;

public record UthoStorageConfig(String endpoint, String accessKey, String secretKey, String region) {
    public UthoStorageConfig {
        endpoint = ValidationUtils.requireNonBlank(endpoint, "endpoint");
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }
        accessKey = ValidationUtils.requireNonBlank(accessKey, "accessKey");
        secretKey = ValidationUtils.requireNonBlank(secretKey, "secretKey");
        region = ValidationUtils.normalizeToNull(region);
        if (region == null) {
            region = "us-east-1";
        }
    }
}
