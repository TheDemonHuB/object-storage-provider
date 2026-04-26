package com.example.objectstorage.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
import org.junit.jupiter.api.Test;

class ProviderClientTest {
    @Test
    void shouldCloseByDefault() {
        ProviderClient client = new ProviderClient() {
            @Override
            public StorageProvider provider() {
                return StorageProvider.S3;
            }

            @Override
            public StoredObject saveFile(String bucket, UploadFileRequest request) {
                return new StoredObject(StorageProvider.S3, bucket, request.key(), "etag", null);
            }

            @Override
            public RetrievedObject getFile(String bucket, GetFileRequest request) {
                return new RetrievedObject(StorageProvider.S3, bucket, request.key(), new byte[]{1}, null, Map.of(), 1L);
            }

            @Override
            public void deleteFile(String bucket, DeleteFileRequest request) {
                // no-op
            }

            @Override
            public List<StorageObjectInfo> listFiles(String bucket, ListFilesRequest request) {
                return List.of();
            }
        };

        assertDoesNotThrow(client::close);
    }
}
