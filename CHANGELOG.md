# Changelog

All notable changes to this project are documented in this file.

## [2.2.2] - 2026-04-27

### Fixed
- Save batch interruption handling now keeps in-flight save count unchanged until a submitted save result is actually consumed.
- Interrupted save batches now drain completed in-flight saves before transactional rollback is attempted.
- Interrupted save batch handling now restores interrupt status before propagating failure after drain/rollback flow.
- S3 save version precondition now treats latest delete markers as the current object version instead of falling back to an older object version.

### Tests
- Added regression coverage for interrupted save batch rollback behavior.
- Added regression coverage for interruption mixed with in-flight execution failures and multi-failure same-chunk handling.
- Added S3 delete-marker version precondition regression coverage.

## [2.2.1] - 2026-04-26

### Added
- Builder setting:
  - `withVersionOverride(boolean)` with default `false`.
- Test app configuration:
  - `storage.version-override` (`false` by default) mapped to library builder.

### Changed
- Save version precondition behavior now supports override strategy:
  - if `UploadFileRequest.versionId` matches current/latest version: save continues.
  - if mismatch and `versionOverride=false`: save fails with conflict exception.
  - if mismatch and `versionOverride=true`: save proceeds and provider creates a new version.

### Documentation
- Added provider setup guidance for AWS/Azure/GCP including:
  - AWS IAM user/policy setup for S3.
  - config-field mapping instructions for library/test-app properties.

## [2.2.0] - 2026-04-26

### Added
- New request model:
  - `GetVersionsRequest(filePath, provider, bucket)`.
- New library API:
  - `getVersions(GetVersionsRequest request)` to return all versions for exact `filePath`.
- Save version precondition support:
  - `UploadFileRequest.versionId` (optional) for optimistic version match before write.

### Changed
- `UploadFileRequest` moved to canonical constructor with `versionId` and no legacy convenience overload.
- Test app API updates:
  - upload endpoint supports optional `versionId`.
  - new endpoint `GET /api/v1/files/versions?filePath=...`.

### Fixed
- Provider save behavior now validates requested expected version before upload:
  - S3/Azure/GCP fail fast with clear mismatch error when provided `versionId` differs from current/latest object version.

## [2.1.0] - 2026-04-26

### Added
- Optional version-aware request fields:
  - `GetFileRequest.versionId`
  - `DeleteFileRequest.versionId`
  - `CopyFileRequest.sourceVersionId`
  - `MoveFileRequest.sourceVersionId`
- Version-aware list metadata:
  - `StorageObjectInfo.versionId` returned from S3, Azure, and GCP providers.

### Changed
- Save path strategy simplified:
  - removed timestamp/timezone path rewriting.
  - save target is now `basePath/<user-filePath>` when `basePath` is configured.
- Request/response field rename for object path fields:
  - `key` -> `filePath`
  - `originalKey` -> `originalFilePath`
- Duplicate file paths in the same save call are no longer renamed by the library.
  - provider-native object versioning is expected for overwrite history.
- Removed timezone configuration usage from builder and test app wiring.
- For get/delete/copy/move:
  - when request version is omitted, library resolves and applies operation across all versions for the exact `filePath`.
- Removed legacy convenience constructors kept for backward compatibility.
  - consumers now use canonical record constructors explicitly.

### Fixed
- Azure provider now returns blob `versionId` in save response when available.
- S3 list now uses version-aware listing and filters directory markers consistently.

## [2.0.0] - 2026-04-26

### Changed
- Breaking: library contract moved to stream-only payload model.

## [1.1.0] - 2026-04-26

### Added
- List-first copy and move APIs.
- Optional provider/bucket overrides and runtime batching controls.

### Changed
- Removed dedicated `UTHO` provider; use S3 endpoint override for S3-compatible providers.

## [1.0.0-SNAPSHOT] - 2026-04-25

### Added
- Initial multi-provider abstraction and test app.
