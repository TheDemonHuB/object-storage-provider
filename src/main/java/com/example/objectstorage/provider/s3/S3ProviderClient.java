package com.example.objectstorage.provider.s3;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import com.example.objectstorage.config.S3StorageConfig;
import com.example.objectstorage.core.ProviderClient;
import com.example.objectstorage.core.ProviderClientSupport;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

public final class S3ProviderClient implements ProviderClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3ProviderClient.class);
    private static final String BUCKET_FIELD = "bucket";

    private final S3Client s3Client;
    private final ProviderClientSupport operations = new ProviderClientSupport("S3", BUCKET_FIELD);

    public S3ProviderClient(S3StorageConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.region()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(config.accessKey(), config.secretKey())
                        )
                )
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(config.pathStyleAccessEnabled())
                                .build()
                );

        if (config.endpointOverride() != null) {
            builder.endpointOverride(URI.create(config.endpointOverride()));
        }
        this.s3Client = builder.build();
    }

    S3ProviderClient(S3Client s3Client) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.S3;
    }

    @Override
    public StoredObject saveFile(String bucket, UploadFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        return operations.executeKeyOperation(LOGGER, "upload", targetBucket, key, () -> {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(key)
                    .metadata(request.metadata())
                    .contentType(request.contentType())
                    .build();
            PutObjectResponse response = s3Client.putObject(putObjectRequest, RequestBody.fromBytes(request.content()));

            StoredObject storedObject = new StoredObject(
                    provider(),
                    targetBucket,
                    key,
                    response.eTag(),
                    response.versionId()
            );
            LOGGER.info("S3 upload completed: bucket={}, key={}", targetBucket, key);
            return storedObject;
        });
    }

    @Override
    public RetrievedObject getFile(String bucket, GetFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        return operations.executeKeyOperation(LOGGER, "download", targetBucket, key, () -> {
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(targetBucket)
                            .key(key)
                            .build()
            );
            GetObjectResponse response = objectBytes.response();
            Long contentLength = response.contentLength();
            long size = contentLength == null ? objectBytes.asByteArray().length : contentLength;

            RetrievedObject retrievedObject = new RetrievedObject(
                    provider(),
                    targetBucket,
                    key,
                    objectBytes.asByteArray(),
                    response.contentType(),
                    response.metadata(),
                    size
            );
            LOGGER.info("S3 download completed: bucket={}, key={}, size={}", targetBucket, key, size);
            return retrievedObject;
        });
    }

    @Override
    public void deleteFile(String bucket, DeleteFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.key();
        operations.executeVoidKeyOperation(LOGGER, "delete", targetBucket, key, () -> {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(targetBucket)
                            .key(key)
                            .build()
            );
            LOGGER.info("S3 delete completed: bucket={}, key={}", targetBucket, key);
        });
    }

    @Override
    public List<StorageObjectInfo> listFiles(String bucket, ListFilesRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String prefix = request.prefix();
        Integer maxResults = request.maxResults();
        return operations.executeListOperation(LOGGER, targetBucket, prefix, maxResults, () -> {
            List<StorageObjectInfo> objects = new ArrayList<>();
            String continuationToken = null;
            boolean truncated;
            do {
                ListObjectsV2Response response = s3Client.listObjectsV2(
                        buildListRequest(targetBucket, prefix, maxResults, objects.size(), continuationToken)
                );
                boolean maxReached = appendPageObjects(response.contents(), objects, maxResults);
                if (maxReached) {
                    return completeList(targetBucket, objects);
                }
                truncated = Boolean.TRUE.equals(response.isTruncated());
                continuationToken = response.nextContinuationToken();
            } while (truncated);

            return completeList(targetBucket, objects);
        });
    }

    private ListObjectsV2Request buildListRequest(
            String bucket,
            String prefix,
            Integer maxResults,
            int collectedCount,
            String continuationToken
    ) {
        ListObjectsV2Request.Builder listRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .continuationToken(continuationToken);
        if (prefix != null) {
            listRequest.prefix(prefix);
        }
        if (maxResults != null) {
            int remaining = maxResults - collectedCount;
            int maxKeys = Math.max(1, Math.min(remaining, 1_000));
            listRequest.maxKeys(maxKeys);
        }
        return listRequest.build();
    }

    private boolean appendPageObjects(List<S3Object> sourceObjects, List<StorageObjectInfo> collected, Integer maxResults) {
        for (S3Object object : sourceObjects) {
            String key = object.key();
            if (!isDirectoryMarker(key)) {
                collected.add(new StorageObjectInfo(
                        key,
                        object.size(),
                        object.lastModified()
                ));
                if (maxResults != null && collected.size() >= maxResults) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<StorageObjectInfo> completeList(String bucket, List<StorageObjectInfo> objects) {
        List<StorageObjectInfo> storageObjectInfos = List.copyOf(objects);
        int itemCount = storageObjectInfos.size();
        LOGGER.info("S3 list completed: bucket={}, itemCount={}", bucket, itemCount);
        return storageObjectInfos;
    }

    @Override
    public void close() {
        s3Client.close();
    }

    private boolean isDirectoryMarker(String key) {
        return key != null && key.endsWith("/");
    }
}
