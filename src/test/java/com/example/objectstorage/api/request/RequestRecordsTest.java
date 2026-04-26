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
        DeleteFileRequest deleteRequest = new DeleteFileRequest("key");
        GetFileRequest getRequest = new GetFileRequest("key");

        assertEquals("key", deleteRequest.key());
        assertEquals("key", getRequest.key());
    }

    @Test
    void shouldRejectBlankKeyForDeleteRequest() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteFileRequest(" "));
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
        CopyFileRequest copyRequest = new CopyFileRequest("source/a.txt", "target/a.txt");
        MoveFileRequest moveRequest = new MoveFileRequest("source/b.txt", "target/b.txt");

        assertEquals("source/a.txt", copyRequest.sourceKey());
        assertEquals("target/a.txt", copyRequest.targetKey());
        assertEquals("source/b.txt", moveRequest.sourceKey());
        assertEquals("target/b.txt", moveRequest.targetKey());
    }

    @Test
    void shouldRejectBlankKeysForCopyAndMoveRequests() {
        assertThrows(IllegalArgumentException.class, () -> new CopyFileRequest(" ", "a.txt"));
        assertThrows(IllegalArgumentException.class, () -> new CopyFileRequest("a.txt", " "));
        assertThrows(IllegalArgumentException.class, () -> new MoveFileRequest(" ", "a.txt"));
        assertThrows(IllegalArgumentException.class, () -> new MoveFileRequest("a.txt", " "));
    }

    @Test
    void shouldValidateUploadRequest() {
        UploadFileRequest request = new UploadFileRequest(
                "docs/a.txt",
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "text/plain",
                Map.of("k", "v")
        );

        assertEquals("docs/a.txt", request.key());
        assertEquals(1L, request.contentLength());
        assertEquals("text/plain", request.contentType());
        assertEquals("v", request.metadata().get("k"));
        assertThrows(IllegalArgumentException.class, () ->
                new UploadFileRequest("a.txt", new ByteArrayInputStream(new byte[]{1}), 0L, null, Map.of()));
    }
}
