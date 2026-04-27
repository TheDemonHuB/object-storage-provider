package com.example.objectstorage.provider.s3;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import com.example.objectstorage.core.ObjectStorageException;
import com.example.objectstorage.config.S3StorageConfig;
import com.example.objectstorage.core.ProviderClient;
import com.example.objectstorage.core.ProviderClientSupport;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public final class S3ProviderClient implements ProviderClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3ProviderClient.class);
    private static final String BUCKET_FIELD = "bucket";

    private final S3Client s3Client;
    private final ProviderClientSupport operations = new ProviderClientSupport("S3", BUCKET_FIELD);
    private final boolean versionOverride;

    public S3ProviderClient(S3StorageConfig config) {
        this(config, false);
    }

    public S3ProviderClient(S3StorageConfig config, boolean versionOverride) {
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
        this.versionOverride = versionOverride;
    }

    S3ProviderClient(S3Client s3Client) {
        this(s3Client, false);
    }

    S3ProviderClient(S3Client s3Client, boolean versionOverride) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
        this.versionOverride = versionOverride;
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.S3;
    }

    @Override
    public StoredObject saveFile(String bucket, UploadFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.filePath();
        return operations.executeKeyOperation(LOGGER, "upload stream", targetBucket, key, () -> {
            operations.validateExpectedVersion(
                    LOGGER,
                    targetBucket,
                    key,
                    request.versionId(),
                    versionOverride,
                    () -> resolveCurrentVersionId(targetBucket, key)
            );
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(key)
                    .metadata(request.metadata())
                    .contentType(request.contentType())
                    .build();
            try (var input = request.content()) {
                PutObjectResponse response = s3Client.putObject(
                        putObjectRequest,
                        RequestBody.fromInputStream(input, request.contentLength())
                );
                StoredObject storedObject = new StoredObject(
                        provider(),
                        targetBucket,
                        key,
                        response.eTag(),
                        response.versionId()
                );
                LOGGER.info("S3 upload stream completed: bucket={}, key={}, size={}", targetBucket, key, request.contentLength());
                return storedObject;
            } catch (IOException ex) {
                throw new ObjectStorageException("S3 upload stream failed while reading content for key: " + key, ex);
            }
        });
    }

    @Override
    public RetrievedObject getFile(String bucket, GetFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.filePath();
        return operations.executeKeyOperation(LOGGER, "download stream", targetBucket, key, () -> {
            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                    buildGetObjectRequest(targetBucket, key, request.versionId())
            );
            GetObjectResponse response = stream.response();
            Long contentLength = response.contentLength();
            long size = contentLength == null ? 0L : contentLength;
            RetrievedObject retrievedObject = new RetrievedObject(
                    provider(),
                    targetBucket,
                    key,
                    response.versionId(),
                    stream,
                    response.contentType(),
                    response.metadata(),
                    size
            );
            LOGGER.info("S3 download stream opened: bucket={}, key={}, size={}", targetBucket, key, size);
            return retrievedObject;
        });
    }

    @Override
    public void deleteFile(String bucket, DeleteFileRequest request) {
        String targetBucket = com.example.objectstorage.core.ValidationUtils.requireNonBlank(bucket, BUCKET_FIELD);
        Objects.requireNonNull(request, ProviderClientSupport.REQUEST_MUST_NOT_BE_NULL);
        String key = request.filePath();
        operations.executeVoidKeyOperation(LOGGER, "delete", targetBucket, key, () -> {
            s3Client.deleteObject(
                    buildDeleteObjectRequest(targetBucket, key, request.versionId())
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
            String keyMarker = null;
            String versionIdMarker = null;
            boolean truncated;
            do {
                ListObjectVersionsResponse response = s3Client.listObjectVersions(
                        buildListVersionsRequest(targetBucket, prefix, maxResults, objects.size(), keyMarker, versionIdMarker)
                );
                boolean maxReached = appendPageVersions(response.versions(), objects, maxResults);
                if (maxReached) {
                    return completeList(targetBucket, objects);
                }
                truncated = Boolean.TRUE.equals(response.isTruncated());
                keyMarker = response.nextKeyMarker();
                versionIdMarker = response.nextVersionIdMarker();
            } while (truncated);

            return completeList(targetBucket, objects);
        });
    }

    private ListObjectVersionsRequest buildListVersionsRequest(
            String bucket,
            String prefix,
            Integer maxResults,
            int collectedCount,
            String keyMarker,
            String versionIdMarker
    ) {
        ListObjectVersionsRequest.Builder listRequest = ListObjectVersionsRequest.builder()
                .bucket(bucket)
                .keyMarker(keyMarker)
                .versionIdMarker(versionIdMarker);
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

    private boolean appendPageVersions(List<ObjectVersion> sourceVersions, List<StorageObjectInfo> collected, Integer maxResults) {
        for (ObjectVersion version : sourceVersions) {
            String key = version.key();
            if (!isDirectoryMarker(key)) {
                collected.add(new StorageObjectInfo(
                        key,
                        version.size(),
                        version.lastModified(),
                        version.versionId()
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

    private GetObjectRequest buildGetObjectRequest(String bucket, String key, String versionId) {
        GetObjectRequest.Builder builder = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        if (versionId != null) {
            builder.versionId(versionId);
        }
        return builder.build();
    }

    private DeleteObjectRequest buildDeleteObjectRequest(String bucket, String key, String versionId) {
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        if (versionId != null) {
            builder.versionId(versionId);
        }
        return builder.build();
    }

    private String resolveCurrentVersionId(String bucket, String key) {
        String keyMarker = null;
        String versionIdMarker = null;
        boolean truncated;
        do {
            ListObjectVersionsResponse response = s3Client.listObjectVersions(
                    ListObjectVersionsRequest.builder()
                            .bucket(bucket)
                            .prefix(key)
                            .keyMarker(keyMarker)
                            .versionIdMarker(versionIdMarker)
                            .maxKeys(1_000)
                            .build()
            );
            String resolvedVersionId = resolveCurrentVersionIdFromPage(key, response);
            if (resolvedVersionId != null) {
                return resolvedVersionId;
            }
            truncated = Boolean.TRUE.equals(response.isTruncated());
            keyMarker = response.nextKeyMarker();
            versionIdMarker = response.nextVersionIdMarker();
        } while (truncated);
        return null;
    }

    private String resolveCurrentVersionIdFromPage(String key, ListObjectVersionsResponse response) {
        VersionResolution deleteMarkerResolution = resolveFromDeleteMarkers(key, response.deleteMarkers());
        if (deleteMarkerResolution.latest()) {
            return deleteMarkerResolution.versionId();
        }
        VersionResolution objectVersionResolution = resolveFromObjectVersions(key, response.versions());
        if (objectVersionResolution.latest()) {
            return objectVersionResolution.versionId();
        }
        return deleteMarkerResolution.versionId() == null
                ? objectVersionResolution.versionId()
                : deleteMarkerResolution.versionId();
    }

    private VersionResolution resolveFromDeleteMarkers(String key, List<DeleteMarkerEntry> deleteMarkers) {
        for (DeleteMarkerEntry deleteMarker : deleteMarkers) {
            if (key.equals(deleteMarker.key())) {
                if (Boolean.TRUE.equals(deleteMarker.isLatest())) {
                    return new VersionResolution(deleteMarker.versionId(), true);
                }
                return new VersionResolution(deleteMarker.versionId(), false);
            }
        }
        return new VersionResolution(null, false);
    }

    private VersionResolution resolveFromObjectVersions(String key, List<ObjectVersion> versions) {
        for (ObjectVersion version : versions) {
            if (key.equals(version.key())) {
                if (Boolean.TRUE.equals(version.isLatest())) {
                    return new VersionResolution(version.versionId(), true);
                }
                return new VersionResolution(version.versionId(), false);
            }
        }
        return new VersionResolution(null, false);
    }

    private record VersionResolution(String versionId, boolean latest) {
    }
}


