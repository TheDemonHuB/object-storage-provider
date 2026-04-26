# Object Storage Library

Provider-agnostic Java library for object storage operations across:
- Amazon S3
- Azure Blob Storage
- Google Cloud Storage
- S3-compatible providers through S3 endpoint override

## Ownership

- Maintainer: `TheDemonHuB`
- GitHub: `https://github.com/TheDemonHuB`

## Version

Current release line: `2.0.0`

## Maven Dependency

```xml
<dependency>
    <groupId>com.example.objectstorage</groupId>
    <artifactId>object-storage-library</artifactId>
    <version>2.0.0</version>
</dependency>
```

## API Surface

`ObjectStorageService` methods:

```java
List<StoredObject> saveFiles(List<UploadFileRequest> requests);
List<RetrievedObject> getFiles(List<GetFileRequest> requests);
List<DeletedObject> deleteFiles(List<DeleteFileRequest> requests);
List<StoredObject> copyFiles(List<CopyFileRequest> requests);
List<StoredObject> moveFiles(List<MoveFileRequest> requests);
List<StorageObjectInfo> listFiles(ListFilesRequest request);
```

All operations are list-first. Single-item defaults still exist (`saveFile`, `getFile`, `deleteFile`) for convenience.

## Important Contract Notes

- Upload uses stream-only request payloads:
  - `UploadFileRequest(String key, InputStream content, long contentLength, String contentType, Map<String, String> metadata, StorageProvider provider, String bucket)`
- Download uses stream-only response payloads:
  - `RetrievedObject` contains `InputStream content` and implements `AutoCloseable`.
- Optional request-level provider/bucket override is supported for upload/get/delete/list/copy/move.
- If request override is absent, builder defaults are used.

## Builder Example

```java
ObjectStorageService service = ObjectStorageServiceBuilder.builder()
        .withS3(s3Config)
        .withAzure(azureConfig)
        .withGcp(gcpConfig)
        .withProvider(StorageProvider.S3)
        .withBucket("my-bucket")
        .withBasePath("documents")
        .withTimeZone("UTC")
        .withBatchSize(100)
        .withMaxConcurrentBatchItems(4)
        .withMaxBatchItems(500)
        .withDefaultListMaxResults(null)
        .withAllowedFileExtensions(List.of("pdf", "txt"))
        .withMaxFileSizeBytes(10_000_000L)
        .build();
```

## Upload Example (Stream)

```java
Path path = Path.of("D:/tmp/sample.txt");
try (InputStream input = Files.newInputStream(path)) {
    List<StoredObject> stored = service.saveFiles(List.of(
            new UploadFileRequest(
                    "docs/sample.txt",
                    input,
                    Files.size(path),
                    "text/plain",
                    Map.of("owner", "team-a")
            )
    ));
}
```

## Download Example (Stream)

```java
RetrievedObject object = service.getFiles(List.of(new GetFileRequest("docs/sample.txt"))).getFirst();
try (object; InputStream input = object.content()) {
    input.transferTo(Files.newOutputStream(Path.of("D:/tmp/sample-downloaded.txt")));
}
```

## Copy And Move

```java
service.copyFiles(List.of(new CopyFileRequest("docs/source.txt", "archive/source.txt")));
service.moveFiles(List.of(new MoveFileRequest("archive/source.txt", "archive/processed/source.txt")));
```

`moveFiles` performs copy then delete with compensating rollback behavior if delete fails.

## List Behavior

```java
service.listFiles(new ListFilesRequest(null, null));
```

- Blank or null `prefix` lists all objects in selected bucket.
- Directory markers (`key` ending with `/`) are filtered out.
- `maxResults` in request overrides builder default when provided.
- `withDefaultListMaxResults(null)` means unlimited by default.

## Save Path Convention

Saved keys are rewritten as:

```text
basePath/yyyy/MM/dd/HHmmssSSS/<user-path>
```

Same-millisecond filename collisions in one save call are handled by filename prefixing:
- `invoice.pdf`
- `1_invoice.pdf`
- `2_invoice.pdf`

Response includes original and stored metadata so caller can map renamed keys safely.

## Logging And Errors

- Library uses SLF4J structured logs for operation start/end, batch boundaries, provider execution, and rollback paths.
- Provider failures are wrapped in `ObjectStorageException`.
- Validation errors throw clear `IllegalArgumentException` / `NullPointerException` messages.

## Test Commands

```powershell
mvn --settings .mvn-settings.xml -pl object-storage-library -am test "-Dsonar.skip=true"
mvn --settings .mvn-settings.xml clean verify
```
