# Object Storage Library

Provider-agnostic Java library for storing and retrieving objects from:

- Amazon S3
- Azure Blob Storage
- Google Cloud Storage
- S3-compatible object storage through S3 custom endpoint configuration

The library exposes one API (`ObjectStorageService`) and hides provider-specific SDK calls behind provider clients.

## Ownership

- Maintainer: `TheDemonHuB`
- GitHub: `https://github.com/TheDemonHuB`

## Requirements

- JDK 21+
- Maven 3.9+
- Valid credentials for at least one supported provider
- Existing bucket/container in the target provider

## Maven Dependency

From the repository root, install the library into your local Maven repository:

```powershell
mvn --settings .mvn-settings.xml -pl object-storage-library -am install "-Dsonar.skip=true"
```

Use it from another Maven application:

```xml
<dependency>
    <groupId>com.example.objectstorage</groupId>
    <artifactId>object-storage-library</artifactId>
    <version>1.1.0</version>
</dependency>
```

For multi-module use inside this repository, depend on the module directly with the same coordinates.

## Main API

Required imports for most applications:

```java
import com.example.objectstorage.api.ObjectStorageService;
import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.MoveFileRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.request.CopyFileRequest;
import com.example.objectstorage.api.response.DeletedObject;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import com.example.objectstorage.config.AzureBlobStorageConfig;
import com.example.objectstorage.config.GcpStorageConfig;
import com.example.objectstorage.config.ObjectStorageServiceBuilder;
import com.example.objectstorage.config.S3StorageConfig;
import com.example.objectstorage.core.ObjectStorageException;
```

```java
ObjectStorageService service = ObjectStorageServiceBuilder.builder()
        .withS3(new S3StorageConfig("ap-south-1", "access", "secret", null, false))
        .withProvider(StorageProvider.S3)
        .withBucket("my-bucket")
        .build();
```

Available operations:

```java
List<StoredObject> saveFiles(List<UploadFileRequest> requests);

List<RetrievedObject> getFiles(List<GetFileRequest> requests);

List<DeletedObject> deleteFiles(List<DeleteFileRequest> requests);

List<StoredObject> copyFiles(List<CopyFileRequest> requests);

List<StoredObject> moveFiles(List<MoveFileRequest> requests);

List<StorageObjectInfo> listFiles(ListFilesRequest request);
```

Save responses contain:

```java
provider
bucket
originalKey
key
originalFilename
storedFilename
eTag
versionId
```

`key` is the final stored object key. Persist that value for later `getFile` and `deleteFile` calls.

`ObjectStorageService` is `AutoCloseable`. Use try-with-resources when the service lifecycle is local to a block:

```java
try (ObjectStorageService service = ObjectStorageServiceBuilder.builder()
        .withS3(new S3StorageConfig("ap-south-1", accessKey, secretKey, null, false))
        .withProvider(StorageProvider.S3)
        .withBucket("my-bucket")
        .build()) {
    // use service
}
```

For Spring applications, create it as a singleton bean and use `destroyMethod = "close"`:

```java
@Bean(destroyMethod = "close")
ObjectStorageService objectStorageService(Environment environment) {
    S3StorageConfig s3 = new S3StorageConfig(
            environment.getRequiredProperty("S3_REGION"),
            environment.getRequiredProperty("S3_ACCESS_KEY"),
            environment.getRequiredProperty("S3_SECRET_KEY"),
            environment.getProperty("S3_ENDPOINT_OVERRIDE"),
            Boolean.parseBoolean(environment.getProperty("S3_PATH_STYLE_ACCESS", "false"))
    );

    return ObjectStorageServiceBuilder.builder()
            .withS3(s3)
            .withProvider(StorageProvider.S3)
            .withBucket("my-bucket")
            .withBasePath("documents")
            .withTimeZone("Asia/Calcutta")
            .withBatchSize(100)
            .withMaxConcurrentBatchItems(4)
            .withMaxBatchItems(500)
            .withDefaultListMaxResults(null)
            .withAllowedFileExtensions(List.of("pdf", "txt"))
            .withMaxFileSizeBytes(10_000_000L)
            .withSingleFileSaveMode(true)
            .build();
}
```

## Provider Configuration

### Amazon S3

```java
S3StorageConfig s3 = new S3StorageConfig(
        "ap-south-1",
        accessKey,
        secretKey,
        null,
        false
);
```

Use `endpointOverride` and `pathStyleAccessEnabled=true` for S3-compatible services that require a custom endpoint.

### Azure Blob Storage

```java
AzureBlobStorageConfig azure = new AzureBlobStorageConfig(connectionString);
```

The library maps `bucket` to the Azure container name.

### Google Cloud Storage

Use either `credentialsPath` or `credentialsJson`.

```java
GcpStorageConfig gcp = new GcpStorageConfig(
        "my-project-id",
        "D:/secrets/gcp-service-account.json",
        null
);
```

If both credential sources are provided, the current implementation uses `credentialsPath`.

When both values are `null`, the Google client falls back to the default Google Cloud credentials chain.

### S3-Compatible Storage (Including Utho)

Configure S3 with a provider endpoint override and path-style access when needed:

```java
S3StorageConfig s3 = new S3StorageConfig(
        "us-east-1",
        accessKey,
        secretKey,
        "https://innoida.utho.io",
        true
);
```

## Build With Multiple Providers

```java
ObjectStorageService service = ObjectStorageServiceBuilder.builder()
        .withS3(s3Config)
        .withAzure(azureConfig)
        .withGcp(gcpConfig)
        .withProvider(StorageProvider.S3)
        .withBucket("my-bucket")
        .build();
```

Builder provider/bucket are defaults. Each request may optionally override provider/bucket.

If a request uses a provider that was not configured in the builder, the service throws `ObjectStorageException`.

## Upload

```java
List<StoredObject> stored = service.saveFiles(List.of(
        new UploadFileRequest(
                "docs/sample-1.txt",
                "hello".getBytes(StandardCharsets.UTF_8),
                "text/plain",
                Map.of("owner", "team-a")
        ),
        new UploadFileRequest(
                "docs/sample-2.txt",
                "world".getBytes(StandardCharsets.UTF_8),
                "text/plain",
                Map.of("owner", "team-a"),
                StorageProvider.GCP,    // optional request override
                "other-bucket"          // optional request override
        )
));
```

Stored key format:

```text
basePath/yyyy/MM/dd/HHmmssSSS/<user-path>
```

Example:

```text
documents/2026/04/26/091530123/customer-123/invoice.pdf
documents/2026/04/26/091530123/customer-123/1_invoice.pdf
```

Notes:

- `key` and `content` are required for every item.
- `provider` and `bucket` are optional per request overrides.
- If request override is absent, builder default provider/bucket are used.
- Empty content is rejected.
- `metadata` may be `null`; it is normalized to an empty map.
- Content bytes are defensively copied.
- Response order matches request order.
- Duplicate filenames within the same save call are renamed at filename level as `1_invoice.pdf`, `2_invoice.pdf`, and so on.
- If any upload in `saveFiles` fails, the library attempts to delete every object successfully saved earlier in that call, then throws `ObjectStorageException`.
- Rollback is best-effort compensation. Object storage providers do not support true SQL-style multi-object transactions.
- Optional builder validations:
  - `withAllowedFileExtensions(List<String>)`
  - `withMaxFileSizeBytes(Long)`
  - no defaults; both are disabled unless configured.

## Download

```java
List<RetrievedObject> objects = service.getFiles(List.of(
        new GetFileRequest("docs/sample-1.txt"),
        new GetFileRequest("docs/sample-2.txt", StorageProvider.GCP, "other-bucket")
));

byte[] firstContent = objects.get(0).content();
```

Use the stored `key` returned by save responses for download requests.

`RetrievedObject` includes provider, bucket, key, content bytes, content type, metadata, and size.

## Delete

```java
List<DeletedObject> deletedObjects = service.deleteFiles(List.of(
        new DeleteFileRequest("docs/sample-1.txt"),
        new DeleteFileRequest("docs/sample-2.txt", StorageProvider.GCP, "other-bucket")
));
```

Delete behavior follows the underlying provider client implementation.
Use the stored `key` returned by save responses for delete requests.

## Copy

```java
List<StoredObject> copiedObjects = service.copyFiles(List.of(
        new CopyFileRequest("docs/source.txt", "archive/source.txt"),
        new CopyFileRequest(
                "docs/source-2.txt",
                "archive/source-2.txt",
                StorageProvider.S3,      // optional source provider override
                "source-bucket",         // optional source bucket override
                StorageProvider.AZURE,   // optional target provider override
                "target-container"       // optional target bucket/container override
        )
));
```

`copyFiles` is provider-agnostic and supports cross-provider copy when both providers are configured.

## Move

```java
List<StoredObject> movedObjects = service.moveFiles(List.of(
        new MoveFileRequest("docs/source.txt", "archive/source.txt")
));
```

`moveFiles` performs `copy` first and then deletes the original key.

## List

```java
List<StorageObjectInfo> objects = service.listFiles(new ListFilesRequest(
        "docs/",
        50,
        null,
        null
));
```

`prefix` and `maxResults` are optional. `maxResults`, when provided, must be greater than zero.  
If `prefix` is blank/null, the library lists all objects in the selected bucket.  
Directory marker objects (`key` ending with `/`) are filtered out from responses.

## Batching And Concurrency

Batch defaults:

```java
batchSize = 100
maxConcurrentBatchItems = 4
maxBatchItems = 500
```

Configure them through the builder:

```java
ObjectStorageService service = ObjectStorageServiceBuilder.builder()
        .withS3(s3Config)
        .withBasePath("documents")
        .withTimeZone("UTC")
        .withBatchSize(100)
        .withMaxConcurrentBatchItems(4)
        .withMaxBatchItems(500)
        .build();
```

Behavior:

- `maxBatchItems` rejects accidental oversized input lists before any provider call starts.
- `batchSize` controls internal chunking.
- `maxConcurrentBatchItems` limits parallel provider calls inside each chunk.
- Responses preserve input order.
- Mixed providers are allowed in the same request list if those providers were configured.
- `getFiles` and `deleteFiles` are fail-fast and non-transactional.
- `saveFiles` uses compensating rollback when a save item fails.

## Save Concurrency

By default, concurrent save calls are allowed.

To allow only one file save at a time per service instance:

```java
ObjectStorageService service = ObjectStorageServiceBuilder.builder()
        .withS3(s3Config)
        .withSingleFileSaveMode(true)
        .build();
```

Equivalent explicit form:

```java
.withMaxConcurrentSaves(1)
```

Use `0` to keep the default unlimited behavior:

```java
.withMaxConcurrentSaves(0)
```

`withSingleFileSaveMode(true)` also applies when callers use `saveFiles`.

## Credential Handling

Do not hardcode credentials in source code.

Recommended patterns:

- Read credentials from environment variables.
- Use a secret manager in production.
- Use local files outside the repository for large credential payloads, such as GCP service account JSON.
- Keep credential files out of git.

Spring example:

```java
String accessKey = environment.getRequiredProperty("S3_ACCESS_KEY");
String secretKey = environment.getRequiredProperty("S3_SECRET_KEY");
```

Suggested environment variable names for consuming applications:

```properties
S3_REGION=ap-south-1
S3_ACCESS_KEY=...
S3_SECRET_KEY=...
S3_ENDPOINT_OVERRIDE=
S3_PATH_STYLE_ACCESS=false
AZURE_STORAGE_CONNECTION_STRING=...
GCP_PROJECT_ID=...
GCP_CREDENTIALS_PATH=D:/secrets/gcp-service-account.json
STORAGE_BATCH_SIZE=100
STORAGE_MAX_CONCURRENT_BATCH_ITEMS=4
STORAGE_MAX_BATCH_ITEMS=500
STORAGE_BASE_PATH=documents
STORAGE_TIME_ZONE=UTC
```

## Error Handling

Provider failures are wrapped in `ObjectStorageException`.

```java
try {
    service.getFiles(List.of(new GetFileRequest("key")));
} catch (ObjectStorageException ex) {
    // log and map to application-specific error handling
}
```

Validation failures use clear Java exceptions such as `IllegalArgumentException` and `NullPointerException` with field-specific messages where possible.

The library uses SLF4J for logs. It logs operation names, provider, bucket/container, key, batch size, item counts, and rollback attempts. It does not log credentials or file content.

## Current Limitations

- The public API currently uses `byte[]` for upload and download content.
- Large-object streaming APIs are not yet available.
- Provider-specific not-found behavior is not normalized across all providers.

The intended product direction is no artificial size restriction, but the current implementation must add streaming upload/download APIs before it is safe for unrestricted large objects.

## Test

From the repository root:

```powershell
mvn --settings .mvn-settings.xml -pl object-storage-library -am test "-Dsonar.skip=true"
```

Run full verification with Sonar:

```powershell
mvn --settings .mvn-settings.xml clean verify
```
