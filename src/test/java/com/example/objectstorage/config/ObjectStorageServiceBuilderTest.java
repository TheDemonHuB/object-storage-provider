package com.example.objectstorage.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.objectstorage.api.ObjectStorageService;
import com.example.objectstorage.api.StorageProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectStorageServiceBuilderTest {
    @Test
    void shouldFailWhenProviderIsMissing() {
        ObjectStorageServiceBuilder builder = ObjectStorageServiceBuilder.builder()
                .withBucket("bucket-a")
                .withS3(sampleS3Config());

        IllegalStateException ex = assertThrows(IllegalStateException.class, builder::build);

        assertEquals("provider must be configured via withProvider(...)", ex.getMessage());
    }

    @Test
    void shouldFailWhenBucketIsMissing() {
        ObjectStorageServiceBuilder builder = ObjectStorageServiceBuilder.builder()
                .withProvider(StorageProvider.S3)
                .withS3(sampleS3Config());

        IllegalStateException ex = assertThrows(IllegalStateException.class, builder::build);

        assertEquals("bucket must be configured via withBucket(...)", ex.getMessage());
    }

    @Test
    void shouldFailWhenSelectedProviderIsNotEnabled() {
        ObjectStorageServiceBuilder builder = ObjectStorageServiceBuilder.builder()
                .withProvider(StorageProvider.GCP)
                .withBucket("bucket-a")
                .withS3(sampleS3Config());

        IllegalStateException ex = assertThrows(IllegalStateException.class, builder::build);

        assertEquals("Configured provider is not enabled: GCP", ex.getMessage());
    }

    @Test
    void shouldBuildServiceWhenSelectedProviderIsEnabled() {
        ObjectStorageService service = ObjectStorageServiceBuilder.builder()
                .withProvider(StorageProvider.S3)
                .withBucket("bucket-a")
                .withS3(sampleS3Config())
                .build();

        assertDoesNotThrow(service::close);
    }

    @Test
    void shouldRejectInvalidBatchAndTimezoneConfig() {
        assertThrows(IllegalArgumentException.class, this::createBuilderWithInvalidBatchSize);
        assertThrows(IllegalArgumentException.class, this::createBuilderWithInvalidConcurrentBatchItems);
        assertThrows(IllegalArgumentException.class, this::createBuilderWithInvalidMaxBatchItems);
        assertThrows(IllegalArgumentException.class, this::createBuilderWithInvalidDefaultListMaxResults);
        assertThrows(IllegalArgumentException.class, this::createBuilderWithInvalidAllowedFileExtensions);
        assertThrows(IllegalArgumentException.class, this::createBuilderWithInvalidMaxFileSizeBytes);
        assertThrows(NullPointerException.class, this::createBuilderWithNullTimeZone);
    }

    @Test
    void shouldBuildServiceWithOptionalSettingsConfigured() {
        ObjectStorageService service = ObjectStorageServiceBuilder.builder()
                .withS3(sampleS3Config())
                .withProvider(StorageProvider.S3)
                .withBucket(" bucket-a ")
                .withBasePath(" docs ")
                .withTimeZone(" UTC ")
                .withBatchSize(25)
                .withMaxConcurrentBatchItems(3)
                .withMaxBatchItems(250)
                .withMaxConcurrentSaves(2)
                .withDefaultListMaxResults(50)
                .withAllowedFileExtensions(List.of(".PDF", " txt "))
                .withMaxFileSizeBytes(10_000L)
                .build();

        assertDoesNotThrow(service::close);
    }

    @Test
    void shouldAllowResettingOptionalSettingsToNull() {
        ObjectStorageService service = ObjectStorageServiceBuilder.builder()
                .withS3(sampleS3Config())
                .withProvider(StorageProvider.S3)
                .withBucket("bucket-a")
                .withSingleFileSaveMode(true)
                .withSingleFileSaveMode(false)
                .withBasePath(" ")
                .withDefaultListMaxResults(null)
                .withAllowedFileExtensions(null)
                .withMaxFileSizeBytes(null)
                .build();

        assertDoesNotThrow(service::close);
    }

    private static S3StorageConfig sampleS3Config() {
        return new S3StorageConfig(
                "ap-south-1",
                "test-access-key",
                "test-secret-key",
                "http://localhost:9000",
                true
        );
    }

    private ObjectStorageServiceBuilder createBuilderWithInvalidBatchSize() {
        return ObjectStorageServiceBuilder.builder().withBatchSize(0);
    }

    private ObjectStorageServiceBuilder createBuilderWithInvalidConcurrentBatchItems() {
        return ObjectStorageServiceBuilder.builder().withMaxConcurrentBatchItems(0);
    }

    private ObjectStorageServiceBuilder createBuilderWithInvalidMaxBatchItems() {
        return ObjectStorageServiceBuilder.builder().withMaxBatchItems(0);
    }

    private ObjectStorageServiceBuilder createBuilderWithInvalidDefaultListMaxResults() {
        return ObjectStorageServiceBuilder.builder().withDefaultListMaxResults(0);
    }

    private ObjectStorageServiceBuilder createBuilderWithInvalidAllowedFileExtensions() {
        return ObjectStorageServiceBuilder.builder().withAllowedFileExtensions(java.util.List.of(" "));
    }

    private ObjectStorageServiceBuilder createBuilderWithInvalidMaxFileSizeBytes() {
        return ObjectStorageServiceBuilder.builder().withMaxFileSizeBytes(0L);
    }

    private ObjectStorageServiceBuilder createBuilderWithNullTimeZone() {
        return ObjectStorageServiceBuilder.builder().withTimeZone(null);
    }
}
