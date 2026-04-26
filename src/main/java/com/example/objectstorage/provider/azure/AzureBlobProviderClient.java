package com.example.objectstorage.provider.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import com.example.objectstorage.config.AzureBlobStorageConfig;
import com.example.objectstorage.core.ProviderClient;
import com.example.objectstorage.core.ProviderClientSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
        String key = request.key();
        return operations.executeKeyOperation(LOGGER, "upload", targetBucket, key, () -> {
            BlobClient blobClient = resolveBlobClient(targetBucket, key);
            blobClient.upload(new ByteArrayInputStream(request.content()), request.content().length, true);

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
                    null
            );
            LOGGER.info("Azure upload completed: container={}, key={}", targetBucket, key);
            return storedObject;
        });
    }

    @Override
    public RetrievedObject getFile(String bucket, GetFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        return operations.executeKeyOperation(LOGGER, "download", targetBucket, key, () -> {
            BlobClient blobClient = resolveBlobClient(targetBucket, key);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.downloadStream(outputStream);
            byte[] bytes = outputStream.toByteArray();
            BlobProperties properties = blobClient.getProperties();

            Long blobSize = properties.getBlobSize();
            long size = blobSize == null ? bytes.length : blobSize;
            Map<String, String> metadata = properties.getMetadata() == null ? Map.of() : properties.getMetadata();

            RetrievedObject retrievedObject = new RetrievedObject(
                    provider(),
                    targetBucket,
                    key,
                    bytes,
                    properties.getContentType(),
                    metadata,
                    size
            );
            LOGGER.info("Azure download completed: container={}, key={}, size={}", targetBucket, key, size);
            return retrievedObject;
        });
    }

    @Override
    public void deleteFile(String bucket, DeleteFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        operations.executeVoidKeyOperation(LOGGER, "delete", targetBucket, key, () -> {
            resolveBlobClient(targetBucket, key).deleteIfExists();
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
                item.getProperties().getLastModified() == null ? null : item.getProperties().getLastModified().toInstant()
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

    private BlobClient resolveBlobClient(String bucket, String key) {
        return resolveContainerClient(bucket).getBlobClient(key);
    }

    private boolean isDirectoryMarker(String key) {
        return key != null && key.endsWith("/");
    }
}
