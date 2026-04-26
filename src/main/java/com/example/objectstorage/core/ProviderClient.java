package com.example.objectstorage.core;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import java.util.List;

/**
 * Internal provider SPI implemented by each cloud provider adapter.
 */
public interface ProviderClient extends AutoCloseable {
    /**
     * Provider identifier implemented by this adapter.
     */
    StorageProvider provider();

    /**
     * Uploads a file to the given bucket/container.
     */
    StoredObject saveFile(String bucket, UploadFileRequest request);

    /**
     * Downloads a file from the given bucket/container.
     */
    RetrievedObject getFile(String bucket, GetFileRequest request);

    /**
     * Deletes a file from the given bucket/container.
     */
    void deleteFile(String bucket, DeleteFileRequest request);

    /**
     * Lists files from the given bucket/container.
     */
    List<StorageObjectInfo> listFiles(String bucket, ListFilesRequest request);

    @Override
    default void close() {
        // no-op by default
    }
}
