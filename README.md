# Object Storage Library

Provider-agnostic Java library for object storage operations across:
- Amazon S3
- Azure Blob Storage
- Google Cloud Storage
- S3-compatible providers via S3 endpoint override

## Version

Current release line: `2.2.2`

## Maven Dependency

```xml
<dependency>
    <groupId>com.example.objectstorage</groupId>
    <artifactId>object-storage-library</artifactId>
    <version>2.2.2</version>
</dependency>
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
- Constructors policy:
  - request/response records are canonical-constructor based.
  - legacy convenience/backward-compat constructor overloads are not retained.
- If version is omitted in `get/delete/copy/move`, the service resolves all versions for that exact `filePath` and runs one operation per version.
- `saveFiles` supports optional `versionId` precondition:
  - when provided, provider checks current/latest version for that `filePath`.
  - mismatch behavior:
    - `versionOverride=false` (default): save fails with version conflict.
    - `versionOverride=true`: save continues and provider creates a new version.
- Interrupted save batches drain in-flight completions before rollback and then rethrow with interrupt status restored.
- `listFiles` returns `StorageObjectInfo(filePath, size, lastModified, versionId)` so callers can target specific versions.
- `getVersions` returns all versions for an exact `filePath`.

## Save Path Strategy

- If builder `basePath` is configured, save target is:

```text
basePath/<user-filePath>
```

- If `basePath` is null/blank, original `filePath` is used as-is.
- Library does not append timestamp/timezone segments.
- Library does not rename duplicate file paths; provider-native versioning should be enabled for overwrite history.
- Save response contains provider `versionId` when available (S3/GCP/Azure when enabled by provider).

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

## Provider Setup Checklist

### AWS S3

1. Enable versioning on the S3 bucket.
2. Create IAM user with programmatic access.
3. Attach permissions for:
   - `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`
   - `s3:ListBucket`, `s3:ListBucketVersions`, `s3:GetBucketVersioning`
4. Configure `S3StorageConfig(region, accessKey, secretKey, endpointOverride, pathStyleAccessEnabled)`.

### Azure Blob

1. Enable blob versioning in the Storage Account (`Data protection`).
2. Create container.
3. Use storage account connection string in `AzureBlobStorageConfig(connectionString)`.

### GCP Storage

1. Enable object versioning on bucket.
2. Create service account and grant storage permissions (object admin for full CRUD).
3. Use `GcpStorageConfig(projectId, credentialsPath, credentialsJson)` with either path or JSON.

## Test

```powershell
.\scripts\activate-local-toolchain.ps1
& ".\.tools\maven\apache-maven-3.9.9\bin\mvn.cmd" -s .mvn-settings.xml -pl object-storage-library -am test
& ".\.tools\maven\apache-maven-3.9.9\bin\mvn.cmd" -s .mvn-settings.xml clean verify
```
