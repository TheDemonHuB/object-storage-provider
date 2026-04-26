package com.example.objectstorage.config;

import com.example.objectstorage.core.ValidationUtils;

public record AzureBlobStorageConfig(String connectionString) {
    public AzureBlobStorageConfig {
        connectionString = ValidationUtils.requireNonBlank(connectionString, "connectionString");
    }
}
