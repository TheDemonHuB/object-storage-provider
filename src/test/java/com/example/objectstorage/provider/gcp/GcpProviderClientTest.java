package com.example.objectstorage.provider.gcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.example.objectstorage.config.GcpStorageConfig;
import com.example.objectstorage.core.ObjectStorageException;
import com.google.api.gax.paging.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.BlobInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GcpProviderClientTest {
    @Test
    void shouldConstructFromConfigWithDefaultVersionOverride() {
        GcpProviderClient client = new GcpProviderClient(new GcpStorageConfig("project-1", null, null));

        assertSame(StorageProvider.GCP, client.provider());
    }

    @Test
    void shouldFailConstructionWhenCredentialsPathIsInvalid() {
        assertThrows(
                ObjectStorageException.class,
                () -> new GcpProviderClient(new GcpStorageConfig("project-1", "D:/missing/service-account.json", null))
        );
    }

    @Test
    void shouldFailConstructionWhenCredentialsJsonIsInvalid() {
        assertThrows(
                ObjectStorageException.class,
                () -> new GcpProviderClient(new GcpStorageConfig("project-1", null, "{\"invalid\":true}"))
        );
    }

    @Test
    void shouldFailConstructionWhenCredentialsPathHasInvalidJson() throws Exception {
        Path credentialsFile = Files.createTempFile("gcp-credentials", ".json");
        try {
            Files.writeString(credentialsFile, "{\"invalid\":true}");
            assertThrows(
                    ObjectStorageException.class,
                    () -> new GcpProviderClient(new GcpStorageConfig("project-1", credentialsFile.toString(), null))
            );
        } finally {
            Files.deleteIfExists(credentialsFile);
        }
    }

    @Test
    void shouldSaveFile() throws Exception {
        Storage storage = mock(Storage.class);
        Blob existingBlob = mock(Blob.class);
        Blob blob = mock(Blob.class);
        when(storage.get(eq(BlobId.of("bucket", "docs/a.txt")))).thenReturn(existingBlob);
        when(existingBlob.getGeneration()).thenReturn(7L);
        when(storage.createFrom(any(BlobInfo.class), any(java.io.InputStream.class))).thenReturn(blob);
        when(blob.getEtag()).thenReturn("etag");
        when(blob.getGeneration()).thenReturn(7L);

        GcpProviderClient client = new GcpProviderClient(storage);
        StoredObject result = client.saveFile("bucket", new UploadFileRequest(
                "docs/a.txt",
                new ByteArrayInputStream(new byte[]{1, 2}),
                2L,
                "text/plain",
                Map.of("owner", "team"),
                "7",
                null,
                null
        ));

        assertEquals("etag", result.eTag());
        assertEquals("7", result.versionId());
    }

    @Test
    void shouldFailSaveWhenExpectedVersionDoesNotMatchCurrentVersion() throws Exception {
        Storage storage = mock(Storage.class);
        Blob existingBlob = mock(Blob.class);
        when(storage.get(eq(BlobId.of("bucket", "docs/a.txt")))).thenReturn(existingBlob);
        when(existingBlob.getGeneration()).thenReturn(2L);

        GcpProviderClient client = new GcpProviderClient(storage);

        assertThrows(
                ObjectStorageException.class,
                () -> client.saveFile("bucket", new UploadFileRequest(
                        "docs/a.txt",
                        new ByteArrayInputStream(new byte[]{1, 2}),
                        2L,
                        "text/plain",
                        Map.of(),
                        "1",
                        null,
                        null
                ))
        );
        verify(storage, never()).createFrom(any(BlobInfo.class), any(java.io.InputStream.class));
    }

    @Test
    void shouldSaveWhenExpectedVersionDoesNotMatchAndOverrideIsEnabled() throws Exception {
        Storage storage = mock(Storage.class);
        Blob existingBlob = mock(Blob.class);
        Blob createdBlob = mock(Blob.class);
        when(storage.get(eq(BlobId.of("bucket", "docs/a.txt")))).thenReturn(existingBlob);
        when(existingBlob.getGeneration()).thenReturn(2L);
        when(storage.createFrom(any(BlobInfo.class), any(java.io.InputStream.class))).thenReturn(createdBlob);
        when(createdBlob.getGeneration()).thenReturn(3L);
        when(createdBlob.getEtag()).thenReturn("etag");

        GcpProviderClient client = new GcpProviderClient(storage, true);

        StoredObject stored = client.saveFile("bucket", new UploadFileRequest(
                "docs/a.txt",
                new ByteArrayInputStream(new byte[]{1, 2}),
                2L,
                "text/plain",
                Map.of(),
                "1",
                null,
                null
        ));

        assertEquals("3", stored.versionId());
        verify(storage).createFrom(any(BlobInfo.class), any(java.io.InputStream.class));
    }

    @Test
    void shouldFailSaveWhenExpectedVersionProvidedButObjectDoesNotExist() throws Exception {
        Storage storage = mock(Storage.class);
        when(storage.get(eq(BlobId.of("bucket", "docs/a.txt")))).thenReturn(null);
        GcpProviderClient client = new GcpProviderClient(storage);

        assertThrows(
                ObjectStorageException.class,
                () -> client.saveFile("bucket", new UploadFileRequest(
                        "docs/a.txt",
                        new ByteArrayInputStream(new byte[]{1}),
                        1L,
                        "text/plain",
                        Map.of(),
                        "1",
                        null,
                        null
                ))
        );
    }

    @Test
    void shouldSaveWhenObjectDoesNotExistAndOverrideIsEnabled() throws Exception {
        Storage storage = mock(Storage.class);
        Blob createdBlob = mock(Blob.class);
        when(storage.get(eq(BlobId.of("bucket", "docs/a.txt")))).thenReturn(null);
        when(storage.createFrom(any(BlobInfo.class), any(java.io.InputStream.class))).thenReturn(createdBlob);
        when(createdBlob.getGeneration()).thenReturn(1L);
        when(createdBlob.getEtag()).thenReturn("etag");

        GcpProviderClient client = new GcpProviderClient(storage, true);

        StoredObject stored = client.saveFile("bucket", new UploadFileRequest(
                "docs/a.txt",
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "text/plain",
                Map.of(),
                "1",
                null,
                null
        ));

        assertEquals("1", stored.versionId());
        verify(storage).createFrom(any(BlobInfo.class), any(java.io.InputStream.class));
    }

    @Test
    void shouldGetFile() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        ReadChannel channel = mock(ReadChannel.class);
        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(blob.reader()).thenReturn(channel);
        when(blob.getMetadata()).thenReturn(Map.of("owner", "team"));
        when(blob.getSize()).thenReturn(3L);
        when(blob.getContentType()).thenReturn("text/plain");

        GcpProviderClient client = new GcpProviderClient(storage);
        RetrievedObject result = client.getFile("bucket", new GetFileRequest("docs/a.txt", null, null, null));

        assertEquals(3L, result.size());
        assertEquals("text/plain", result.contentType());
        assertEquals("team", result.metadata().get("owner"));
    }

    @Test
    void shouldFailGetWhenObjectIsMissing() {
        Storage storage = mock(Storage.class);
        when(storage.get(any(BlobId.class))).thenReturn(null);

        GcpProviderClient client = new GcpProviderClient(storage);

        assertThrows(
                ObjectStorageException.class,
                () -> client.getFile("bucket", new GetFileRequest("docs/a.txt", null, null, null))
        );
    }

    @Test
    void shouldDeleteFile() {
        Storage storage = mock(Storage.class);
        GcpProviderClient client = new GcpProviderClient(storage);

        client.deleteFile("bucket", new DeleteFileRequest("docs/a.txt", null, null, null));

        verify(storage).delete(any(BlobId.class));
    }

    @Test
    void shouldSupportVersionedGetAndDeleteRequests() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        ReadChannel channel = mock(ReadChannel.class);
        when(storage.get(eq(BlobId.of("bucket", "docs/a.txt", 5L)))).thenReturn(blob);
        when(blob.reader()).thenReturn(channel);
        when(blob.getMetadata()).thenReturn(Map.of());
        when(blob.getSize()).thenReturn(1L);
        GcpProviderClient client = new GcpProviderClient(storage);

        client.getFile("bucket", new GetFileRequest("docs/a.txt", "5", null, null));
        client.deleteFile("bucket", new DeleteFileRequest("docs/a.txt", "5", null, null));

        verify(storage).get(eq(BlobId.of("bucket", "docs/a.txt", 5L)));
        verify(storage).delete(eq(BlobId.of("bucket", "docs/a.txt", 5L)));
    }

    @Test
    void shouldFailVersionedGetWhenVersionIsInvalidNumber() {
        Storage storage = mock(Storage.class);
        GcpProviderClient client = new GcpProviderClient(storage);

        assertThrows(
                ObjectStorageException.class,
                () -> client.getFile("bucket", new GetFileRequest("docs/a.txt", "bad-version", null, null))
        );
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
    void shouldPassPrefixToListingOptions() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        @SuppressWarnings("unchecked")
        Page<Blob> page = mock(Page.class);
        when(blob.getName()).thenReturn("docs/a.txt");
        when(blob.getSize()).thenReturn(2L);
        when(page.iterateAll()).thenReturn(List.of(blob));
        when(storage.list(eq("bucket"), any(Storage.BlobListOption[].class))).thenReturn(page);

        GcpProviderClient client = new GcpProviderClient(storage);
        List<StorageObjectInfo> listed = client.listFiles("bucket", new ListFilesRequest("docs/", 10));

        assertEquals(1, listed.size());
    }

    @Test
    void shouldStopListingWhenMaxResultsReached() {
        Storage storage = mock(Storage.class);
        Blob first = mock(Blob.class);
        Blob second = mock(Blob.class);
        @SuppressWarnings("unchecked")
        Page<Blob> page = mock(Page.class);
        when(first.getName()).thenReturn("docs/a.txt");
        when(first.getSize()).thenReturn(1L);
        when(first.getUpdateTimeOffsetDateTime()).thenReturn(null);
        when(second.getName()).thenReturn("docs/b.txt");
        when(second.getSize()).thenReturn(2L);
        when(second.getUpdateTimeOffsetDateTime()).thenReturn(null);
        when(page.iterateAll()).thenReturn(List.of(first, second));
        when(storage.list(eq("bucket"), any(Storage.BlobListOption[].class))).thenReturn(page);

        GcpProviderClient client = new GcpProviderClient(storage);
        List<StorageObjectInfo> listed = client.listFiles("bucket", new ListFilesRequest("docs/", 1));

        assertEquals(1, listed.size());
        assertEquals("docs/a.txt", listed.getFirst().filePath());
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
    void shouldWrapUploadIOException() throws Exception {
        Storage storage = mock(Storage.class);
        Blob existingBlob = mock(Blob.class);
        Blob createdBlob = mock(Blob.class);
        when(storage.get(eq(BlobId.of("bucket", "docs/a.txt")))).thenReturn(existingBlob);
        when(existingBlob.getGeneration()).thenReturn(1L);
        when(storage.createFrom(any(BlobInfo.class), any(java.io.InputStream.class))).thenReturn(createdBlob);

        GcpProviderClient client = new GcpProviderClient(storage);

        assertThrows(
                ObjectStorageException.class,
                () -> client.saveFile("bucket", new UploadFileRequest(
                        "docs/a.txt",
                        new CloseFailingInputStream(),
                        1L,
                        "text/plain",
                        Map.of(),
                        "1",
                        null,
                        null
                ))
        );
    }

    @Test
    void shouldReturnProvider() {
        GcpProviderClient client = new GcpProviderClient(mock(Storage.class));

        assertSame(StorageProvider.GCP, client.provider());
    }

    private static final class FailingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("boom");
        }
    }

    private static final class CloseFailingInputStream extends InputStream {
        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("boom");
        }
    }
}
