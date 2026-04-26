package com.example.objectstorage.provider.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import com.example.objectstorage.config.AzureBlobStorageConfig;
import com.example.objectstorage.core.ObjectStorageException;
import com.example.objectstorage.core.ProviderClient;
import com.example.objectstorage.core.ProviderClientSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AzureBlobProviderClient implements ProviderClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureBlobProviderClient.class);
    private static final String BUCKET_FIELD = "bucket";

    private final BlobServiceClient blobServiceClient;
    private final ProviderClientSupport operations = new ProviderClientSupport("Azure", "container");

    public AzureBlobProviderClient(AzureBlobStorageConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(config.connectionString())
                .buildClient();
    }

    AzureBlobProviderClient(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = Objects.requireNonNull(blobServiceClient, "blobServiceClient must not be null");
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.AZURE;
    }

    @Override
    public StoredObject saveFile(String bucket, UploadFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.filePath();
        return operations.executeKeyOperation(LOGGER, "upload stream", targetBucket, key, () -> {
            operations.validateExpectedVersion(LOGGER, targetBucket, key, request.versionId(), () -> resolveCurrentVersionId(targetBucket, key));
            BlobClient blobClient = resolveBlobClient(targetBucket, key, null);
            try (var input = request.content()) {
                blobClient.upload(input, request.contentLength(), true);
            } catch (Exception ex) {
                throw new ObjectStorageException(
                        "Azure upload stream failed for container/key: " + targetBucket + "/" + key,
                        ex
                );
            }

            if (request.contentType() != null) {
                blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(request.contentType()));
            }
            if (!request.metadata().isEmpty()) {
                blobClient.setMetadata(request.metadata());
            }

            BlobProperties properties = blobClient.getProperties();
            StoredObject storedObject = new StoredObject(
                    provider(),
                    targetBucket,
                    key,
                    properties.getETag(),
                    properties.getVersionId()
            );
            LOGGER.info("Azure upload stream completed: container={}, key={}, size={}", targetBucket, key, request.contentLength());
            return storedObject;
        });
    }

    @Override
    public RetrievedObject getFile(String bucket, GetFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.filePath();
        return operations.executeKeyOperation(LOGGER, "download stream", targetBucket, key, () -> {
            BlobClient blobClient = resolveBlobClient(targetBucket, key, request.versionId());
            BlobProperties properties = blobClient.getProperties();
            BlobInputStream stream = blobClient.openInputStream();

            Long blobSize = properties.getBlobSize();
            long size = blobSize == null ? 0L : blobSize;
            Map<String, String> metadata = properties.getMetadata() == null ? Map.of() : properties.getMetadata();

            RetrievedObject retrievedObject = new RetrievedObject(
                    provider(),
                    targetBucket,
                    key,
                    properties.getVersionId(),
                    stream,
                    properties.getContentType(),
                    metadata,
                    size
            );
            LOGGER.info("Azure download stream opened: container={}, key={}, size={}", targetBucket, key, size);
            return retrievedObject;
        });
    }

    @Override
    public void deleteFile(String bucket, DeleteFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.filePath();
        operations.executeVoidKeyOperation(LOGGER, "delete", targetBucket, key, () -> {
            resolveBlobClient(targetBucket, key, request.versionId()).deleteIfExists();
            LOGGER.info("Azure delete completed: container={}, key={}", targetBucket, key);
        });
    }

    @Override
    public List<StorageObjectInfo> listFiles(String bucket, ListFilesRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String prefix = request.prefix();
        Integer maxResults = request.maxResults();
        return operations.executeListOperation(LOGGER, targetBucket, prefix, maxResults, () -> {
            BlobContainerClient containerClient = resolveContainerClient(targetBucket);
            ListBlobsOptions options = new ListBlobsOptions();
            if (prefix != null) {
                options.setPrefix(prefix);
            }
            options.setDetails(new BlobListDetails().setRetrieveVersions(true));

            List<StorageObjectInfo> objects = new ArrayList<>();
            for (BlobItem item : containerClient.listBlobs(options, null)) {
                if (addBlobIfFile(item, objects) && maxResults != null && objects.size() >= maxResults) {
                    return completeList(targetBucket, objects);
                }
            }
            return completeList(targetBucket, objects);
        });
    }

    private boolean addBlobIfFile(BlobItem item, List<StorageObjectInfo> objects) {
        String key = item.getName();
        if (isDirectoryMarker(key)) {
            return false;
        }
        long size = item.getProperties().getContentLength() == null ? 0 : item.getProperties().getContentLength();
        objects.add(new StorageObjectInfo(
                key,
                size,
                item.getProperties().getLastModified() == null ? null : item.getProperties().getLastModified().toInstant(),
                item.getVersionId()
        ));
        return true;
    }

    private List<StorageObjectInfo> completeList(String container, List<StorageObjectInfo> objects) {
        List<StorageObjectInfo> storageObjectInfos = List.copyOf(objects);
        int itemCount = storageObjectInfos.size();
        LOGGER.info("Azure list completed: container={}, itemCount={}", container, itemCount);
        return storageObjectInfos;
    }

    private BlobContainerClient resolveContainerClient(String bucket) {
        return blobServiceClient.getBlobContainerClient(bucket);
    }

    private BlobClient resolveBlobClient(String bucket, String key, String versionId) {
        BlobClient blobClient = resolveContainerClient(bucket).getBlobClient(key);
        return versionId == null ? blobClient : blobClient.getVersionClient(versionId);
    }

    private boolean isDirectoryMarker(String key) {
        return key != null && key.endsWith("/");
    }

    private String resolveCurrentVersionId(String bucket, String key) {
        BlobClient blobClient = resolveBlobClient(bucket, key, null);
        try {
            return blobClient.getProperties().getVersionId();
        } catch (BlobStorageException ex) {
            if (ex.getStatusCode() == 404) {
                return null;
            }
            throw ex;
        }
    }
}


