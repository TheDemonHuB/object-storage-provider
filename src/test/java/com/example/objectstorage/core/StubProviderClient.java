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
import java.util.Map;

class StubProviderClient implements ProviderClient {
    private final StorageProvider provider;

    StubProviderClient(StorageProvider provider) {
        this.provider = provider;
    }

    @Override
    public StorageProvider provider() {
        return provider;
    }

    @Override
    public StoredObject saveFile(String bucket, UploadFileRequest request) {
        return new StoredObject(provider, bucket, request.key(), "etag", null);
    }

    @Override
    public RetrievedObject getFile(String bucket, GetFileRequest request) {
        return new RetrievedObject(
                provider,
                bucket,
                request.key(),
                new byte[]{1},
                "application/octet-stream",
                Map.of(),
                1L
        );
    }

    @Override
    public void deleteFile(String bucket, DeleteFileRequest request) {
        // no-op
    }

    @Override
    public List<StorageObjectInfo> listFiles(String bucket, ListFilesRequest request) {
        return List.of(new StorageObjectInfo("dummy-key", 1L, null));
    }
}
