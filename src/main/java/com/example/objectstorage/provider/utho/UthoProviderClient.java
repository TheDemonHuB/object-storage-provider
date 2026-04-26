package com.example.objectstorage.provider.utho;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import com.example.objectstorage.config.UthoStorageConfig;
import com.example.objectstorage.core.ProviderClient;
import com.example.objectstorage.core.ProviderClientSupport;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import okhttp3.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UthoProviderClient implements ProviderClient {
    private static final String USER_META_PREFIX = "x-amz-meta-";
    private static final Logger LOGGER = LoggerFactory.getLogger(UthoProviderClient.class);
    private static final String BUCKET_FIELD = "bucket";

    private final MinioClient minioClient;
    private final ProviderClientSupport operations = new ProviderClientSupport("Utho", BUCKET_FIELD);

    public UthoProviderClient(UthoStorageConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.minioClient = MinioClient.builder()
                .endpoint(config.endpoint())
                .credentials(config.accessKey(), config.secretKey())
                .region(config.region())
                .build();
    }

    UthoProviderClient(MinioClient minioClient) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient must not be null");
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.UTHO;
    }

    @Override
    public StoredObject saveFile(String bucket, UploadFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        return operations.executeKeyOperation(LOGGER, "upload", targetBucket, key, () -> {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(request.content())) {
                PutObjectArgs.Builder putObjectArgs = PutObjectArgs.builder()
                        .bucket(targetBucket)
                        .object(key)
                        .stream(inputStream, request.content().length, -1);
                if (request.contentType() != null) {
                    putObjectArgs.contentType(request.contentType());
                }
                if (!request.metadata().isEmpty()) {
                    putObjectArgs.userMetadata(request.metadata());
                }

                ObjectWriteResponse response = minioClient.putObject(putObjectArgs.build());
                StoredObject storedObject = new StoredObject(
                        provider(),
                        targetBucket,
                        key,
                        response.etag(),
                        response.versionId()
                );
                LOGGER.info("Utho upload completed: bucket={}, key={}", targetBucket, key);
                return storedObject;
            } catch (Exception ex) {
                throw new com.example.objectstorage.core.ObjectStorageException(
                        "Utho upload failed for bucket/key: " + targetBucket + "/" + key,
                        ex
                );
            }
        });
    }

    @Override
    public RetrievedObject getFile(String bucket, GetFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        return operations.executeKeyOperation(LOGGER, "download", targetBucket, key, () -> {
            try (GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(targetBucket)
                            .object(key)
                            .build())
            ) {
                byte[] bytes = response.readAllBytes();
                Headers headers = response.headers();
                String contentType = headers.get("Content-Type");

                long size = bytes.length;
                String contentLengthHeader = headers.get("Content-Length");
                if (contentLengthHeader != null && !contentLengthHeader.isBlank()) {
                    size = Long.parseLong(contentLengthHeader);
                }

                RetrievedObject retrievedObject = new RetrievedObject(
                        provider(),
                        targetBucket,
                        key,
                        bytes,
                        contentType,
                        extractUserMetadata(headers),
                        size
                );
                LOGGER.info("Utho download completed: bucket={}, key={}, size={}", targetBucket, key, size);
                return retrievedObject;
            } catch (Exception ex) {
                throw new com.example.objectstorage.core.ObjectStorageException(
                        "Utho download failed for bucket/key: " + targetBucket + "/" + key,
                        ex
                );
            }
        });
    }

    @Override
    public void deleteFile(String bucket, DeleteFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        operations.executeVoidKeyOperation(LOGGER, "delete", targetBucket, key, () -> {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(targetBucket)
                                .object(key)
                                .build()
                );
                LOGGER.info("Utho delete completed: bucket={}, key={}", targetBucket, key);
            } catch (Exception ex) {
                throw new com.example.objectstorage.core.ObjectStorageException(
                        "Utho delete failed for bucket/key: " + targetBucket + "/" + key,
                        ex
                );
            }
        });
    }

    @Override
    public List<StorageObjectInfo> listFiles(String bucket, ListFilesRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String prefix = request.prefix();
        Integer maxResults = request.maxResults();
        return operations.executeListOperation(LOGGER, targetBucket, prefix, maxResults, () -> {
            try {
                ListObjectsArgs.Builder listObjectsArgs = ListObjectsArgs.builder()
                        .bucket(targetBucket)
                        .recursive(true);
                if (prefix != null) {
                    listObjectsArgs.prefix(prefix);
                }
                if (maxResults != null) {
                    listObjectsArgs.maxKeys(maxResults);
                }

                List<StorageObjectInfo> objects = new ArrayList<>();
                Iterable<Result<Item>> results = minioClient.listObjects(listObjectsArgs.build());
                for (Result<Item> result : results) {
                    Item item = result.get();
                    if (addItemIfFile(item, objects) && maxResults != null && objects.size() >= maxResults) {
                        return completeList(targetBucket, objects);
                    }
                }
                return completeList(targetBucket, objects);
            } catch (Exception ex) {
                throw new com.example.objectstorage.core.ObjectStorageException(
                        "Utho list failed for bucket: " + targetBucket,
                        ex
                );
            }
        });
    }

    private boolean addItemIfFile(Item item, List<StorageObjectInfo> objects) {
        String key = item.objectName();
        if (isDirectoryMarker(key)) {
            return false;
        }
        objects.add(new StorageObjectInfo(
                key,
                item.size(),
                item.lastModified() == null ? null : item.lastModified().toInstant()
        ));
        return true;
    }

    private List<StorageObjectInfo> completeList(String bucket, List<StorageObjectInfo> objects) {
        List<StorageObjectInfo> storageObjectInfos = List.copyOf(objects);
        int itemCount = storageObjectInfos.size();
        LOGGER.info("Utho list completed: bucket={}, itemCount={}", bucket, itemCount);
        return storageObjectInfos;
    }

    private Map<String, String> extractUserMetadata(Headers headers) {
        Map<String, String> metadata = new HashMap<>();
        for (String headerName : headers.names()) {
            String normalized = headerName.toLowerCase(Locale.ROOT);
            if (normalized.startsWith(USER_META_PREFIX)) {
                String key = normalized.substring(USER_META_PREFIX.length());
                metadata.put(key, headers.get(headerName));
            }
        }
        return Map.copyOf(metadata);
    }

    private boolean isDirectoryMarker(String key) {
        return key != null && key.endsWith("/");
    }
}
