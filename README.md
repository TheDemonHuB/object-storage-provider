# Object Storage Library

[![Maven Central](https://img.shields.io/maven-central/v/io.github.thedemonhub/object-storage-library?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.thedemonhub/object-storage-library)
[![Java 21](https://img.shields.io/badge/Java-21-007396)](https://adoptium.net/)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## Why This Exists

Most teams do not want storage provider logic spread across business code. This library exists to give you one clean Java API for upload/download/delete/list/version operations across S3, Azure Blob, and GCP Storage so you can switch providers, run multi-cloud, or migrate with minimal code change.

## 10-Second Quick Start

1. Add dependency:

```xml
<dependency>
    <groupId>io.github.thedemonhub</groupId>
    <artifactId>object-storage-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

2. Build service:

```java
ObjectStorageService service = ObjectStorageServiceBuilder.builder()
        .withProvider(StorageProvider.S3)
        .withBucket("my-bucket")
        .withS3(new S3StorageConfig("ap-south-1", "<accessKey>", "<secretKey>", null, false))
        .build();
```

3. Save a file:

```java
service.saveFiles(List.of(new UploadFileRequest(
        "docs/a.txt",
        inputStream,
        contentLength,
        "text/plain",
        Map.of(),
        null,
        StorageProvider.S3,
        "my-bucket"
)));
```

## Supported Providers

| Provider | Status | Notes |
|---|---|---|
| Amazon S3 | Supported | Includes version-aware operations |
| Azure Blob Storage | Supported | Uses connection string configuration |
| Google Cloud Storage | Supported | Supports credentials path or inline JSON |
| S3-compatible endpoints | Supported | Through S3 endpoint override |

## Why Not Just Use SDKs?

Direct SDK usage is valid for small, single-provider workflows. This library is useful when you need:
- one contract across providers
- consistent request/response models for your app and tests
- version-aware behavior handled in one place
- centralized validation, batching, and logging in service layer code

## Comparison

| Capability | This Library | Direct SDKs |
|---|---|---|
| Single API across clouds | Yes | No |
| Provider switching effort | Low | High |
| Version-aware batch behavior | Built-in | Custom per provider |
| App-level input validation | Built-in | Custom |
| Code duplication in business layer | Low | Medium to High |
| Provider-specific advanced tuning | Selective | Full control |

## Real-World Use Cases

- Multi-cloud product with tenant-level provider selection (`S3`, `AZURE`, `GCP`)
- Migration from one cloud provider to another with minimal service-layer rewrites
- SaaS platforms that need strict file versioning behavior for audit/compliance
- Internal platforms that want a single storage abstraction shared by multiple services

## Builder Example

```java
ObjectStorageService service = ObjectStorageServiceBuilder.builder()
        .withS3(s3Config)
        .withProvider(StorageProvider.S3)
        .withBucket("my-bucket")
        .withBasePath("documents")
        .withBatchSize(100)
        .withMaxConcurrentBatchItems(4)
        .withMaxBatchItems(500)
        .withDefaultListMaxResults(null)
        .withVersionOverride(false)
        .withAllowedFileExtensions(List.of("pdf", "txt"))
        .withMaxFileSizeBytes(10_000_000L)
        .build();
```

## Service API

```java
List<StoredObject> saveFiles(List<UploadFileRequest> requests);
List<RetrievedObject> getFiles(List<GetFileRequest> requests);
List<DeletedObject> deleteFiles(List<DeleteFileRequest> requests);
List<StoredObject> copyFiles(List<CopyFileRequest> requests);
List<StoredObject> moveFiles(List<MoveFileRequest> requests);
List<StorageObjectInfo> listFiles(ListFilesRequest request);
List<StorageObjectInfo> getVersions(GetVersionsRequest request);
```

`get/delete/copy/move` request records include optional version fields:
- `GetFileRequest.versionId`
- `DeleteFileRequest.versionId`
- `CopyFileRequest.sourceVersionId`
- `MoveFileRequest.sourceVersionId`

## Contract Notes

- Upload request uses stream payload:
  - `UploadFileRequest(String filePath, InputStream content, long contentLength, String contentType, Map<String, String> metadata, String versionId, StorageProvider provider, String bucket)`
- Download response uses stream payload:
  - `RetrievedObject` includes `InputStream content` and is `AutoCloseable`.
- Field naming uses `filePath` and `originalFilePath`.
- API records are canonical-constructor based only.
- If version is omitted in `get/delete/copy/move`, the service resolves all versions for the exact `filePath` and applies operation per version.
- `saveFiles` optional `versionId` is a precondition against current/latest version:
  - `versionOverride=false` (default): mismatch fails with conflict.
  - `versionOverride=true`: mismatch is allowed and provider creates a new version.
- Interrupted save batches drain in-flight completions before rollback and rethrow with interrupt status restored.
- `listFiles` returns `StorageObjectInfo(filePath, size, lastModified, versionId)`.
- `getVersions` returns all versions for an exact `filePath`.

## Save Path Strategy

- If `basePath` is configured, save target is `basePath/<user-filePath>`.
- If `basePath` is null/blank, original `filePath` is used as-is.
- No timestamp/timezone path rewriting is applied.
- Duplicate file paths are not renamed; rely on provider-native versioning.
- Save response includes provider `versionId` when available.

## Provider Setup Checklist

### AWS S3

1. Enable bucket versioning.
2. Create IAM user with programmatic access.
3. Attach required permissions:
   - `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`
   - `s3:ListBucket`, `s3:ListBucketVersions`, `s3:GetBucketVersioning`
4. Configure `S3StorageConfig(region, accessKey, secretKey, endpointOverride, pathStyleAccessEnabled)`.

### Azure Blob

1. Enable blob versioning in storage account data protection.
2. Create container.
3. Configure `AzureBlobStorageConfig(connectionString)`.

### GCP Storage

1. Enable object versioning on bucket.
2. Create service account with storage permissions.
3. Configure `GcpStorageConfig(projectId, credentialsPath, credentialsJson)` with path or inline JSON.
