package com.example.objectstorage.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
import org.junit.jupiter.api.Test;

class ObjectStorageServiceTest {
    @Test
    void shouldCloseByDefault() {
        ObjectStorageService service = new RecordingObjectStorageService();

        assertDoesNotThrow(service::close);
    }

    private static final class RecordingObjectStorageService implements ObjectStorageService {
        @Override
        public List<StoredObject> saveFiles(List<UploadFileRequest> requests) {
            return List.of();
        }

        @Override
        public List<RetrievedObject> getFiles(List<GetFileRequest> requests) {
            return List.of();
        }

        @Override
        public List<DeletedObject> deleteFiles(List<DeleteFileRequest> requests) {
            return List.of();
        }

        @Override
        public List<StoredObject> copyFiles(List<CopyFileRequest> requests) {
            return List.of();
        }

        @Override
        public List<StoredObject> moveFiles(List<MoveFileRequest> requests) {
            return List.of();
        }

        @Override
        public List<StorageObjectInfo> listFiles(ListFilesRequest request) {
            return List.of();
        }
    }
}
