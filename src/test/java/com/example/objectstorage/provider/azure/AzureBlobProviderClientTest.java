package com.example.objectstorage.provider.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import com.example.objectstorage.core.ObjectStorageException;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzureBlobProviderClientTest {
    @Test
    void shouldSaveFile() {
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        BlobProperties properties = mock(BlobProperties.class);
        when(serviceClient.getBlobContainerClient("docs")).thenReturn(containerClient);
        when(containerClient.getBlobClient("customer/a.txt")).thenReturn(blobClient);
        when(blobClient.getProperties()).thenReturn(properties);
        when(properties.getETag()).thenReturn("etag");
        when(properties.getVersionId()).thenReturn("v1");

        AzureBlobProviderClient client = new AzureBlobProviderClient(serviceClient);
        StoredObject result = client.saveFile("docs", uploadRequest());

        assertEquals("etag", result.eTag());
        verify(blobClient).upload(any(), eq(3L), eq(true));
        verify(blobClient).setMetadata(Map.of("owner", "team"));
    }

    @Test
    void shouldFailSaveWhenExpectedVersionDoesNotMatchCurrentVersion() {
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        BlobProperties properties = mock(BlobProperties.class);
        when(serviceClient.getBlobContainerClient("docs")).thenReturn(containerClient);
        when(containerClient.getBlobClient("customer/a.txt")).thenReturn(blobClient);
        when(blobClient.getProperties()).thenReturn(properties);
        when(properties.getVersionId()).thenReturn("v2");

        AzureBlobProviderClient client = new AzureBlobProviderClient(serviceClient);

        assertThrows(
                ObjectStorageException.class,
                () -> client.saveFile("docs", new UploadFileRequest(
                        "customer/a.txt",
                        new ByteArrayInputStream(new byte[]{1}),
                        1L,
                        "text/plain",
                        Map.of(),
                        "v1",
                        null,
                        null
                ))
        );
        verify(blobClient, never()).upload(any(), eq(1L), eq(true));
    }

    @Test
    void shouldGetFile() throws Exception {
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        BlobProperties properties = mock(BlobProperties.class);
        when(serviceClient.getBlobContainerClient("docs")).thenReturn(containerClient);
        when(containerClient.getBlobClient("customer/a.txt")).thenReturn(blobClient);
        when(blobClient.getProperties()).thenReturn(properties);
        when(properties.getBlobSize()).thenReturn(4L);
        when(properties.getContentType()).thenReturn("text/plain");
        when(properties.getMetadata()).thenReturn(null);
        when(blobClient.openInputStream()).thenReturn(mock(com.azure.storage.blob.specialized.BlobInputStream.class));

        AzureBlobProviderClient client = new AzureBlobProviderClient(serviceClient);
        RetrievedObject result = client.getFile("docs", new GetFileRequest("customer/a.txt", null, null, null));

        assertEquals(4L, result.size());
        assertEquals("text/plain", result.contentType());
        assertEquals(Map.of(), result.metadata());
    }

    @Test
    void shouldDeleteFile() {
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        when(serviceClient.getBlobContainerClient("docs")).thenReturn(containerClient);
        when(containerClient.getBlobClient("customer/a.txt")).thenReturn(blobClient);

        AzureBlobProviderClient client = new AzureBlobProviderClient(serviceClient);
        client.deleteFile("docs", new DeleteFileRequest("customer/a.txt", null, null, null));

        verify(blobClient).deleteIfExists();
    }

    @Test
    void shouldSupportVersionedGetAndDeleteRequests() throws Exception {
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        BlobClient versionBlobClient = mock(BlobClient.class);
        BlobProperties properties = mock(BlobProperties.class);
        when(serviceClient.getBlobContainerClient("docs")).thenReturn(containerClient);
        when(containerClient.getBlobClient("customer/a.txt")).thenReturn(blobClient);
        when(blobClient.getVersionClient("v5")).thenReturn(versionBlobClient);
        when(versionBlobClient.getProperties()).thenReturn(properties);
        when(versionBlobClient.openInputStream()).thenReturn(mock(com.azure.storage.blob.specialized.BlobInputStream.class));

        AzureBlobProviderClient client = new AzureBlobProviderClient(serviceClient);
        client.getFile("docs", new GetFileRequest("customer/a.txt", "v5", null, null));
        client.deleteFile("docs", new DeleteFileRequest("customer/a.txt", "v5", null, null));

        verify(blobClient, times(2)).getVersionClient("v5");
        verify(versionBlobClient).deleteIfExists();
    }

    @Test
    void shouldFailSaveWhenExpectedVersionProvidedButBlobDoesNotExist() {
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        BlobStorageException notFound = mock(BlobStorageException.class);
        when(notFound.getStatusCode()).thenReturn(404);
        when(serviceClient.getBlobContainerClient("docs")).thenReturn(containerClient);
        when(containerClient.getBlobClient("customer/a.txt")).thenReturn(blobClient);
        when(blobClient.getProperties()).thenThrow(notFound);

        AzureBlobProviderClient client = new AzureBlobProviderClient(serviceClient);

        assertThrows(
                ObjectStorageException.class,
                () -> client.saveFile("docs", new UploadFileRequest(
                        "customer/a.txt",
                        new ByteArrayInputStream(new byte[]{1}),
                        1L,
                        "text/plain",
                        Map.of(),
                        "v1",
                        null,
                        null
                ))
        );
    }

    @Test
    void shouldListFiles() {
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        BlobItem markerItem = mock(BlobItem.class);
        BlobItem item = mock(BlobItem.class);
        BlobItemProperties markerProperties = mock(BlobItemProperties.class);
        BlobItemProperties properties = mock(BlobItemProperties.class);
        @SuppressWarnings("unchecked")
        PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
        when(serviceClient.getBlobContainerClient("docs")).thenReturn(containerClient);
        when(containerClient.listBlobs(any(), eq(null))).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(List.of(markerItem, item).iterator());
        when(markerItem.getName()).thenReturn("customer/");
        when(markerItem.getProperties()).thenReturn(markerProperties);
        when(markerProperties.getContentLength()).thenReturn(0L);
        when(markerProperties.getLastModified()).thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        when(item.getName()).thenReturn("customer/a.txt");
        when(item.getProperties()).thenReturn(properties);
        when(properties.getContentLength()).thenReturn(5L);
        when(properties.getLastModified()).thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        AzureBlobProviderClient client = new AzureBlobProviderClient(serviceClient);
        List<StorageObjectInfo> result = client.listFiles("docs", new ListFilesRequest("customer/", 10));

        assertEquals(1, result.size());
        assertEquals("customer/a.txt", result.getFirst().filePath());
        assertEquals(5L, result.getFirst().size());
    }

    @Test
    void shouldReturnProvider() {
        AzureBlobProviderClient client = new AzureBlobProviderClient(mock(BlobServiceClient.class));

        assertSame(StorageProvider.AZURE, client.provider());
    }

    private static UploadFileRequest uploadRequest() {
        return new UploadFileRequest(
                "customer/a.txt",
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3L,
                "text/plain",
                Map.of("owner", "team"),
                "v1",
                null,
                null
        );
    }
}

