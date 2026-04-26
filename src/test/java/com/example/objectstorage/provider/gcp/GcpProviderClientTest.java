package com.example.objectstorage.provider.gcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.BlobInfo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GcpProviderClientTest {
    @Test
    void shouldSaveFile() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(blob);
        when(blob.getEtag()).thenReturn("etag");
        when(blob.getGeneration()).thenReturn(7L);

        GcpProviderClient client = new GcpProviderClient(storage);
        StoredObject result = client.saveFile("bucket", new UploadFileRequest(
                "docs/a.txt",
                new byte[]{1, 2},
                "text/plain",
                Map.of("owner", "team")
        ));

        assertEquals("etag", result.eTag());
        assertEquals("7", result.versionId());
    }

    @Test
    void shouldGetFile() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(blob.getContent()).thenReturn(new byte[]{1, 2, 3});
        when(blob.getMetadata()).thenReturn(Map.of("owner", "team"));
        when(blob.getSize()).thenReturn(3L);
        when(blob.getContentType()).thenReturn("text/plain");

        GcpProviderClient client = new GcpProviderClient(storage);
        RetrievedObject result = client.getFile("bucket", new GetFileRequest("docs/a.txt"));

        assertEquals(3L, result.size());
        assertEquals("text/plain", result.contentType());
        assertEquals("team", result.metadata().get("owner"));
    }

    @Test
    void shouldDeleteFile() {
        Storage storage = mock(Storage.class);
        GcpProviderClient client = new GcpProviderClient(storage);

        client.deleteFile("bucket", new DeleteFileRequest("docs/a.txt"));

        verify(storage).delete(any(BlobId.class));
    }

    @Test
    void shouldMapUpdateTimeToInstantWhenPresent() {
        Storage storage = mock(Storage.class);
        Blob markerBlob = mock(Blob.class);
        Blob blob = mock(Blob.class);
        @SuppressWarnings("unchecked")
        Page<Blob> page = mock(Page.class);

        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        when(markerBlob.getName()).thenReturn("docs/");
        when(markerBlob.getSize()).thenReturn(0L);
        when(markerBlob.getUpdateTimeOffsetDateTime()).thenReturn(updatedAt);
        when(blob.getName()).thenReturn("docs/a.txt");
        when(blob.getSize()).thenReturn(42L);
        when(blob.getUpdateTimeOffsetDateTime()).thenReturn(updatedAt);
        when(page.iterateAll()).thenReturn(List.of(markerBlob, blob));
        when(storage.list(eq("bucket"), any(Storage.BlobListOption[].class))).thenReturn(page);

        GcpProviderClient client = new GcpProviderClient(storage);
        List<StorageObjectInfo> result = client.listFiles(
                "bucket",
                new ListFilesRequest(null, 10));

        assertEquals(1, result.size());
        assertEquals(updatedAt.toInstant(), result.getFirst().lastModified());
        assertEquals(42L, result.getFirst().size());
    }

    @Test
    void shouldHandleMissingUpdateTimeAndSize() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        @SuppressWarnings("unchecked")
        Page<Blob> page = mock(Page.class);

        when(blob.getName()).thenReturn("docs/b.txt");
        when(blob.getSize()).thenReturn(null);
        when(blob.getUpdateTimeOffsetDateTime()).thenReturn(null);
        when(page.iterateAll()).thenReturn(List.of(blob));
        when(storage.list(eq("bucket"), any(Storage.BlobListOption[].class))).thenReturn(page);

        GcpProviderClient client = new GcpProviderClient(storage);
        List<StorageObjectInfo> result = client.listFiles(
                "bucket",
                new ListFilesRequest(null, 10));

        assertEquals(1, result.size());
        assertEquals(0L, result.getFirst().size());
        assertNull(result.getFirst().lastModified());
    }

    @Test
    void shouldReturnProvider() {
        GcpProviderClient client = new GcpProviderClient(mock(Storage.class));

        assertSame(StorageProvider.GCP, client.provider());
    }
}
