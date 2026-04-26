package com.example.objectstorage.api;

import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.GetVersionsRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.MoveFileRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.request.CopyFileRequest;
import com.example.objectstorage.api.response.DeletedObject;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import java.util.List;

/**
 * Public provider-agnostic object storage contract.
 *
 * <p>All methods are list-first so callers can use one or many items with a single API shape.
 */
public interface ObjectStorageService extends AutoCloseable {
    /**
     * Saves files to object storage and returns stored metadata in the same order as the input.
     */
    List<StoredObject> saveFiles(List<UploadFileRequest> requests);

    /**
     * Retrieves files from object storage in the same order as the input.
     */
    List<RetrievedObject> getFiles(List<GetFileRequest> requests);

    /**
     * Deletes files from object storage in the same order as the input.
     */
    List<DeletedObject> deleteFiles(List<DeleteFileRequest> requests);

    /**
     * Copies files between source and destination targets.
     */
    List<StoredObject> copyFiles(List<CopyFileRequest> requests);

    /**
     * Moves files by copying to destination and deleting the source object.
     */
    List<StoredObject> moveFiles(List<MoveFileRequest> requests);

    /**
     * Lists files for a prefix and optional result cap.
     */
    List<StorageObjectInfo> listFiles(ListFilesRequest request);

    /**
     * Lists all available versions for an exact file path.
     */
    List<StorageObjectInfo> getVersions(GetVersionsRequest request);

    @Override
    default void close() {
        // no-op by default
    }
}
