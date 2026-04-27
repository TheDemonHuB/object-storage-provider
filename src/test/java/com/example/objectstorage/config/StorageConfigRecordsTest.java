package com.example.objectstorage.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StorageConfigRecordsTest {
    @Test
    void shouldValidateAzureConnectionString() {
        AzureBlobStorageConfig config = new AzureBlobStorageConfig("DefaultEndpointsProtocol=https;AccountName=test;");

        assertEquals("DefaultEndpointsProtocol=https;AccountName=test;", config.connectionString());
        assertThrows(IllegalArgumentException.class, () -> new AzureBlobStorageConfig(" "));
    }

    @Test
    void shouldNormalizeGcpConfigValues() {
        GcpStorageConfig config = new GcpStorageConfig(" project-1 ", "  ", " {\"type\":\"service_account\"} ");

        assertEquals("project-1", config.projectId());
        assertNull(config.credentialsPath());
        assertEquals("{\"type\":\"service_account\"}", config.credentialsJson());
    }

}
