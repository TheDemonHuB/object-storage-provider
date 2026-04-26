package com.example.objectstorage.provider.gcp;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageOptions;
import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import com.example.objectstorage.config.GcpStorageConfig;
import com.example.objectstorage.core.ObjectStorageException;
import com.example.objectstorage.core.ProviderClient;
import com.example.objectstorage.core.ProviderClientSupport;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GcpProviderClient implements ProviderClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GcpProviderClient.class);
    private static final String BUCKET_FIELD = "bucket";

    private final Storage storage;
    private final ProviderClientSupport operations = new ProviderClientSupport("GCP", BUCKET_FIELD);

    public GcpProviderClient(GcpStorageConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.storage = createStorageClient(config);
    }

    GcpProviderClient(Storage storage) {
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.GCP;
    }

    @Override
    public StoredObject saveFile(String bucket, UploadFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        return operations.executeKeyOperation(LOGGER, "upload", targetBucket, key, () -> {
            BlobInfo.Builder infoBuilder = BlobInfo.newBuilder(BlobId.of(targetBucket, key));
            if (request.contentType() != null) {
                infoBuilder.setContentType(request.contentType());
            }
            if (!request.metadata().isEmpty()) {
                infoBuilder.setMetadata(request.metadata());
            }

            Blob createdBlob = storage.create(infoBuilder.build(), request.content());
            String version = createdBlob.getGeneration() == null ? null : String.valueOf(createdBlob.getGeneration());
            StoredObject storedObject = new StoredObject(
                    provider(),
                    targetBucket,
                    key,
                    createdBlob.getEtag(),
                    version
            );
            LOGGER.info("GCP upload completed: bucket={}, key={}", targetBucket, key);
            return storedObject;
        });
    }

    @Override
    public RetrievedObject getFile(String bucket, GetFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        return operations.executeKeyOperation(LOGGER, "download", targetBucket, key, () -> {
            Blob blob = storage.get(BlobId.of(targetBucket, key));
            if (blob == null) {
                throw new ObjectStorageException("GCP object not found: " + targetBucket + "/" + key);
            }

            byte[] bytes = blob.getContent();
            Map<String, String> metadata = blob.getMetadata() == null ? Map.of() : blob.getMetadata();
            Long size = blob.getSize();

            RetrievedObject retrievedObject = new RetrievedObject(
                    provider(),
                    targetBucket,
                    key,
                    bytes,
                    blob.getContentType(),
                    metadata,
                    size == null ? bytes.length : size
            );
            LOGGER.info(
                    "GCP download completed: bucket={}, key={}, size={}",
                    targetBucket,
                    key,
                    size == null ? bytes.length : size
            );
            return retrievedObject;
        });
    }

    @Override
    public void deleteFile(String bucket, DeleteFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        operations.executeVoidKeyOperation(LOGGER, "delete", targetBucket, key, () -> {
            storage.delete(BlobId.of(targetBucket, key));
            LOGGER.info("GCP delete completed: bucket={}, key={}", targetBucket, key);
        });
    }

    @Override
    public List<StorageObjectInfo> listFiles(String bucket, ListFilesRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String prefix = request.prefix();
        Integer maxResults = request.maxResults();
        return operations.executeListOperation(LOGGER, targetBucket, prefix, maxResults, () -> {
            Page<Blob> blobs = storage.list(targetBucket, createListOptions(prefix, maxResults));
            List<StorageObjectInfo> objects = new ArrayList<>();
            for (Blob blob : blobs.iterateAll()) {
                if (addBlobIfFile(blob, objects) && maxResults != null && objects.size() >= maxResults) {
                    return completeList(targetBucket, objects);
                }
            }
            return completeList(targetBucket, objects);
        });
    }

    private boolean addBlobIfFile(Blob blob, List<StorageObjectInfo> objects) {
        String key = blob.getName();
        if (isDirectoryMarker(key)) {
            return false;
        }
        Long size = blob.getSize();
        OffsetDateTime updatedAt = blob.getUpdateTimeOffsetDateTime();
        objects.add(new StorageObjectInfo(
                key,
                size == null ? 0 : size,
                updatedAt == null ? null : Instant.from(updatedAt)
        ));
        return true;
    }

    private List<StorageObjectInfo> completeList(String bucket, List<StorageObjectInfo> objects) {
        List<StorageObjectInfo> storageObjectInfos = List.copyOf(objects);
        int itemCount = storageObjectInfos.size();
        LOGGER.info("GCP list completed: bucket={}, itemCount={}", bucket, itemCount);
        return storageObjectInfos;
    }

    private BlobListOption[] createListOptions(String prefix, Integer maxResults) {
        List<BlobListOption> options = new ArrayList<>();
        if (prefix != null) {
            options.add(BlobListOption.prefix(prefix));
        }
        if (maxResults != null) {
            options.add(BlobListOption.pageSize(maxResults));
        }
        return options.toArray(BlobListOption[]::new);
    }

    private Storage createStorageClient(GcpStorageConfig config) {
        try {
            StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder();

            if (config.projectId() != null) {
                optionsBuilder.setProjectId(config.projectId());
            }

            if (config.credentialsPath() != null) {
                try (FileInputStream input = new FileInputStream(config.credentialsPath())) {
                    GoogleCredentials credentials = ServiceAccountCredentials.fromStream(input);
                    optionsBuilder.setCredentials(credentials);
                }
            } else if (config.credentialsJson() != null) {
                try (ByteArrayInputStream input = new ByteArrayInputStream(config.credentialsJson().getBytes(StandardCharsets.UTF_8))) {
                    GoogleCredentials credentials = ServiceAccountCredentials.fromStream(input);
                    optionsBuilder.setCredentials(credentials);
                }
            }

            Storage service = optionsBuilder.build().getService();
            boolean projectIdConfigured = config.projectId() != null;
            LOGGER.info("GCP storage client initialized: projectIdConfigured={}", projectIdConfigured);
            return service;
        } catch (Exception ex) {
            throw new ObjectStorageException("Unable to initialize GCP storage client", ex);
        }
    }

    private boolean isDirectoryMarker(String key) {
        return key != null && key.endsWith("/");
    }
}
