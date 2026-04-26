package com.example.objectstorage.config;

import com.example.objectstorage.core.ValidationUtils;

public record GcpStorageConfig(String projectId, String credentialsPath, String credentialsJson) {
    public GcpStorageConfig {
        projectId = ValidationUtils.normalizeToNull(projectId);
        credentialsPath = ValidationUtils.normalizeToNull(credentialsPath);
        credentialsJson = ValidationUtils.normalizeToNull(credentialsJson);
    }
}
