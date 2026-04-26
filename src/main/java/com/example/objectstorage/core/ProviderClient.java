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

public interface ProviderClient extends AutoCloseable {
    StorageProvider provider();

    StoredObject saveFile(String bucket, UploadFileRequest request);

    RetrievedObject getFile(String bucket, GetFileRequest request);

    void deleteFile(String bucket, DeleteFileRequest request);

    List<StorageObjectInfo> listFiles(String bucket, ListFilesRequest request);

    @Override
    default void close() {
        // no-op by default
    }
}
