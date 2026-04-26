package com.example.objectstorage.api.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestRecordsTest {
    @Test
    void shouldCreateDeleteAndGetRequestsWithValidData() {
        DeleteFileRequest deleteRequest = new DeleteFileRequest("key", null, null, null);
        GetFileRequest getRequest = new GetFileRequest("key", null, null, null);

        assertEquals("key", deleteRequest.filePath());
        assertEquals("key", getRequest.filePath());
    }

    @Test
    void shouldRejectBlankKeyForDeleteRequest() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteFileRequest(" ", null, null, null));
    }

    @Test
    void shouldNormalizeListPrefixAndValidateMaxResults() {
        ListFilesRequest request = new ListFilesRequest("   ", 10);

        assertNull(request.prefix());
        assertEquals(10, request.maxResults());
        assertThrows(IllegalArgumentException.class, () ->
                new ListFilesRequest("prefix", 0));
    }

    @Test
    void shouldCreateCopyAndMoveRequestsWithValidData() {
        CopyFileRequest copyRequest = new CopyFileRequest("source/a.txt", null, "target/a.txt", null, null, null, null);
        MoveFileRequest moveRequest = new MoveFileRequest("source/b.txt", null, "target/b.txt", null, null, null, null);

        assertEquals("source/a.txt", copyRequest.sourceKey());
        assertEquals("target/a.txt", copyRequest.targetKey());
        assertEquals("source/b.txt", moveRequest.sourceKey());
        assertEquals("target/b.txt", moveRequest.targetKey());
    }

    @Test
    void shouldRejectBlankKeysForCopyAndMoveRequests() {
        assertThrows(IllegalArgumentException.class, () -> new CopyFileRequest(" ", null, "a.txt", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CopyFileRequest("a.txt", null, " ", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new MoveFileRequest(" ", null, "a.txt", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new MoveFileRequest("a.txt", null, " ", null, null, null, null));
    }

    @Test
    void shouldValidateUploadRequest() {
        UploadFileRequest request = new UploadFileRequest(
                "docs/a.txt",
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "text/plain",
                Map.of("k", "v"),
                "v1",
                null,
                null
        );

        assertEquals("docs/a.txt", request.filePath());
        assertEquals(1L, request.contentLength());
        assertEquals("text/plain", request.contentType());
        assertEquals("v", request.metadata().get("k"));
        assertEquals("v1", request.versionId());
        assertThrows(IllegalArgumentException.class, () ->
                new UploadFileRequest("a.txt", new ByteArrayInputStream(new byte[]{1}), 0L, null, Map.of(), null, null, null));
    }

    @Test
    void shouldCreateAndValidateGetVersionsRequest() {
        GetVersionsRequest request = new GetVersionsRequest("docs/a.txt", null, " ");

        assertEquals("docs/a.txt", request.filePath());
        assertNull(request.bucket());
        assertThrows(IllegalArgumentException.class, () -> new GetVersionsRequest(" ", null, null));
    }
}

