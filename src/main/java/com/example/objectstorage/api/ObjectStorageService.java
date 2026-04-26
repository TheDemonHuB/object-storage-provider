package com.example.objectstorage.api;

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
import java.util.List;

public interface ObjectStorageService extends AutoCloseable {
    List<StoredObject> saveFiles(List<UploadFileRequest> requests);

    List<RetrievedObject> getFiles(List<GetFileRequest> requests);

    List<DeletedObject> deleteFiles(List<DeleteFileRequest> requests);

    List<StoredObject> copyFiles(List<CopyFileRequest> requests);

    List<StoredObject> moveFiles(List<MoveFileRequest> requests);

    List<StorageObjectInfo> listFiles(ListFilesRequest request);

    @Override
    default void close() {
        // no-op by default
    }
}
