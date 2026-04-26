package com.example.objectstorage.provider.utho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
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
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.Result;
import io.minio.messages.Item;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;

class UthoProviderClientTest {
    @Test
    void shouldSaveFile() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        ObjectWriteResponse response = mock(ObjectWriteResponse.class);
        when(minioClient.putObject(any())).thenReturn(response);
        when(response.etag()).thenReturn("etag");
        when(response.versionId()).thenReturn("v1");

        UthoProviderClient client = new UthoProviderClient(minioClient);
        StoredObject result = client.saveFile("docs", uploadRequest());

        assertEquals("etag", result.eTag());
        assertEquals("v1", result.versionId());
    }

    @Test
    void shouldGetFile() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(minioClient.getObject(any())).thenReturn(response);
        when(response.readAllBytes()).thenReturn(new byte[]{1, 2, 3});
        when(response.headers()).thenReturn(Headers.of(Map.of(
                "Content-Type", "text/plain",
                "Content-Length", "3",
                "x-amz-meta-owner", "team"
        )));

        UthoProviderClient client = new UthoProviderClient(minioClient);
        RetrievedObject result = client.getFile("docs", new GetFileRequest("customer/a.txt"));

        assertEquals(3L, result.size());
        assertEquals("text/plain", result.contentType());
        assertEquals("team", result.metadata().get("owner"));
    }

    @Test
    void shouldDeleteFile() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UthoProviderClient client = new UthoProviderClient(minioClient);

        client.deleteFile("docs", new DeleteFileRequest("customer/a.txt"));

        verify(minioClient).removeObject(any());
    }

    @Test
    void shouldListFiles() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        @SuppressWarnings("unchecked")
        Result<Item> markerResultItem = mock(Result.class);
        @SuppressWarnings("unchecked")
        Result<Item> resultItem = mock(Result.class);
        Item markerItem = mock(Item.class);
        Item item = mock(Item.class);
        when(minioClient.listObjects(any())).thenReturn(List.of(markerResultItem, resultItem));
        when(markerResultItem.get()).thenReturn(markerItem);
        when(markerItem.objectName()).thenReturn("customer/");
        when(markerItem.size()).thenReturn(0L);
        when(markerItem.lastModified()).thenReturn(ZonedDateTime.parse("2026-01-01T00:00:00Z"));
        when(resultItem.get()).thenReturn(item);
        when(item.objectName()).thenReturn("customer/a.txt");
        when(item.size()).thenReturn(7L);
        when(item.lastModified()).thenReturn(ZonedDateTime.parse("2026-01-01T00:00:00Z"));

        UthoProviderClient client = new UthoProviderClient(minioClient);
        List<StorageObjectInfo> result = client.listFiles("docs", new ListFilesRequest("customer/", 10));

        assertEquals(1, result.size());
        assertEquals("customer/a.txt", result.getFirst().key());
        assertEquals(7L, result.getFirst().size());
    }

    @Test
    void shouldReturnProvider() {
        UthoProviderClient client = new UthoProviderClient(mock(MinioClient.class));

        assertSame(StorageProvider.UTHO, client.provider());
    }

    private static UploadFileRequest uploadRequest() {
        return new UploadFileRequest(
                "customer/a.txt",
                new byte[]{1, 2, 3},
                "text/plain",
                Map.of("owner", "team")
        );
    }
}
