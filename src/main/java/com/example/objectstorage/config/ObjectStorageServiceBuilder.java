package com.example.objectstorage.config;

import com.example.objectstorage.api.ObjectStorageService;
import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.core.DefaultObjectStorageService;
import com.example.objectstorage.core.ProviderClient;
import com.example.objectstorage.provider.azure.AzureBlobProviderClient;
import com.example.objectstorage.provider.gcp.GcpProviderClient;
import com.example.objectstorage.provider.s3.S3ProviderClient;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ObjectStorageServiceBuilder {
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int DEFAULT_MAX_CONCURRENT_BATCH_ITEMS = 4;
    public static final int DEFAULT_MAX_BATCH_ITEMS = 500;
    public static final Integer DEFAULT_LIST_MAX_RESULTS = null;
    public static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("UTC");

    private S3StorageConfig s3StorageConfig;
    private AzureBlobStorageConfig azureBlobStorageConfig;
    private GcpStorageConfig gcpStorageConfig;
    private int maxConcurrentSaves;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private int maxConcurrentBatchItems = DEFAULT_MAX_CONCURRENT_BATCH_ITEMS;
    private int maxBatchItems = DEFAULT_MAX_BATCH_ITEMS;
    private Integer defaultListMaxResults = DEFAULT_LIST_MAX_RESULTS;
    private Set<String> allowedFileExtensions;
    private Long maxFileSizeBytes;
    private String basePath;
    private ZoneId timeZone = DEFAULT_TIME_ZONE;
    private StorageProvider provider;
    private String bucket;

    private ObjectStorageServiceBuilder() {
    }

    public static ObjectStorageServiceBuilder builder() {
        return new ObjectStorageServiceBuilder();
    }

    public ObjectStorageServiceBuilder withS3(S3StorageConfig config) {
        this.s3StorageConfig = Objects.requireNonNull(config, "s3 config must not be null");
        return this;
    }

    public ObjectStorageServiceBuilder withAzure(AzureBlobStorageConfig config) {
        this.azureBlobStorageConfig = Objects.requireNonNull(config, "azure config must not be null");
        return this;
    }

    public ObjectStorageServiceBuilder withGcp(GcpStorageConfig config) {
        this.gcpStorageConfig = Objects.requireNonNull(config, "gcp config must not be null");
        return this;
    }

    public ObjectStorageServiceBuilder withMaxConcurrentSaves(int maxConcurrentSaves) {
        if (maxConcurrentSaves < 0) {
            throw new IllegalArgumentException("maxConcurrentSaves must not be negative");
        }
        this.maxConcurrentSaves = maxConcurrentSaves;
        return this;
    }

    public ObjectStorageServiceBuilder withSingleFileSaveMode(boolean enabled) {
        this.maxConcurrentSaves = enabled ? 1 : 0;
        return this;
    }

    public ObjectStorageServiceBuilder withBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        this.batchSize = batchSize;
        return this;
    }

    public ObjectStorageServiceBuilder withMaxConcurrentBatchItems(int maxConcurrentBatchItems) {
        if (maxConcurrentBatchItems <= 0) {
            throw new IllegalArgumentException("maxConcurrentBatchItems must be greater than 0");
        }
        this.maxConcurrentBatchItems = maxConcurrentBatchItems;
        return this;
    }

    public ObjectStorageServiceBuilder withMaxBatchItems(int maxBatchItems) {
        if (maxBatchItems <= 0) {
            throw new IllegalArgumentException("maxBatchItems must be greater than 0");
        }
        this.maxBatchItems = maxBatchItems;
        return this;
    }

    public ObjectStorageServiceBuilder withDefaultListMaxResults(Integer defaultListMaxResults) {
        if (defaultListMaxResults != null && defaultListMaxResults <= 0) {
            throw new IllegalArgumentException("defaultListMaxResults must be greater than 0");
        }
        this.defaultListMaxResults = defaultListMaxResults;
        return this;
    }

    public ObjectStorageServiceBuilder withAllowedFileExtensions(List<String> allowedFileExtensions) {
        if (allowedFileExtensions == null) {
            this.allowedFileExtensions = null;
            return this;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String extension : allowedFileExtensions) {
            if (extension == null || extension.isBlank()) {
                throw new IllegalArgumentException("allowedFileExtensions must not contain blank values");
            }
            String normalizedExtension = extension.trim().toLowerCase(Locale.ROOT);
            if (normalizedExtension.startsWith(".")) {
                normalizedExtension = normalizedExtension.substring(1);
            }
            if (normalizedExtension.isBlank()) {
                throw new IllegalArgumentException("allowedFileExtensions must not contain blank values");
            }
            normalized.add(normalizedExtension);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("allowedFileExtensions must not be empty");
        }
        this.allowedFileExtensions = Collections.unmodifiableSet(new LinkedHashSet<>(normalized));
        return this;
    }

    public ObjectStorageServiceBuilder withMaxFileSizeBytes(Long maxFileSizeBytes) {
        if (maxFileSizeBytes != null && maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must be greater than 0");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
        return this;
    }

    public ObjectStorageServiceBuilder withBasePath(String basePath) {
        this.basePath = basePath == null || basePath.isBlank() ? null : basePath.trim();
        return this;
    }

    public ObjectStorageServiceBuilder withTimeZone(String timeZone) {
        Objects.requireNonNull(timeZone, "timeZone must not be null");
        this.timeZone = ZoneId.of(timeZone.trim());
        return this;
    }

    public ObjectStorageServiceBuilder withProvider(StorageProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        return this;
    }

    public ObjectStorageServiceBuilder withBucket(String bucket) {
        this.bucket = bucket == null || bucket.isBlank() ? null : bucket.trim();
        return this;
    }

    public ObjectStorageService build() {
        Map<StorageProvider, ProviderClient> providers = new EnumMap<>(StorageProvider.class);

        if (s3StorageConfig != null) {
            providers.put(StorageProvider.S3, new S3ProviderClient(s3StorageConfig));
        }
        if (azureBlobStorageConfig != null) {
            providers.put(StorageProvider.AZURE, new AzureBlobProviderClient(azureBlobStorageConfig));
        }
        if (gcpStorageConfig != null) {
            providers.put(StorageProvider.GCP, new GcpProviderClient(gcpStorageConfig));
        }
        if (provider == null) {
            throw new IllegalStateException("provider must be configured via withProvider(...)");
        }
        if (bucket == null) {
            throw new IllegalStateException("bucket must be configured via withBucket(...)");
        }
        if (!providers.containsKey(provider)) {
            throw new IllegalStateException("Configured provider is not enabled: " + provider);
        }

        return new DefaultObjectStorageService(
                providers,
                provider,
                bucket,
                maxConcurrentSaves,
                new DefaultObjectStorageService.ServiceSettings(
                        batchSize,
                        maxConcurrentBatchItems,
                        maxBatchItems,
                        defaultListMaxResults,
                        allowedFileExtensions == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(allowedFileExtensions)),
                        maxFileSizeBytes,
                        basePath,
                        timeZone,
                        Clock.system(timeZone)
                )
        );
    }
}
