package com.example.objectstorage.api.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
