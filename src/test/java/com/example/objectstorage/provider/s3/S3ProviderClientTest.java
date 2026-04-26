package com.example.objectstorage.provider.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

class S3ProviderClientTest {
    @Test
    void shouldSaveFile() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").versionId("v1").build());

        S3ProviderClient client = new S3ProviderClient(s3Client);
        StoredObject result = client.saveFile("docs", uploadRequest());

        assertEquals("etag", result.eTag());
        assertEquals("v1", result.versionId());
    }

    @Test
    void shouldGetFile() {
        S3Client s3Client = mock(S3Client.class);
        GetObjectResponse response = GetObjectResponse.builder()
                .contentLength(4L)
                .contentType("text/plain")
                .metadata(Map.of("owner", "team"))
                .build();
        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> stream = mock(ResponseInputStream.class);
        when(stream.response()).thenReturn(response);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);

        S3ProviderClient client = new S3ProviderClient(s3Client);
        RetrievedObject result = client.getFile("docs", new GetFileRequest("customer/a.txt"));

        assertEquals(4L, result.size());
        assertEquals("text/plain", result.contentType());
        assertEquals("team", result.metadata().get("owner"));
    }

    @Test
    void shouldDeleteFile() {
        S3Client s3Client = mock(S3Client.class);
        S3ProviderClient client = new S3ProviderClient(s3Client);

        client.deleteFile("docs", new DeleteFileRequest("customer/a.txt"));

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void shouldListFiles() {
        S3Client s3Client = mock(S3Client.class);
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("customer/").size(0L).lastModified(Instant.parse("2026-01-01T00:00:00Z")).build(),
                        S3Object.builder().key("customer/a.txt").size(12L).lastModified(Instant.parse("2026-01-01T00:00:00Z")).build(),
                        S3Object.builder().key("customer/b.txt").size(8L).lastModified(Instant.parse("2026-01-02T00:00:00Z")).build()
                )
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        S3ProviderClient client = new S3ProviderClient(s3Client);
        List<StorageObjectInfo> result = client.listFiles("docs", new ListFilesRequest("customer/", 10));

        assertEquals(2, result.size());
        assertEquals("customer/a.txt", result.getFirst().key());
        assertEquals(8L, result.get(1).size());
    }

    @Test
    void shouldListFilesAcrossPagesWhenMaxResultsNotProvided() {
        S3Client s3Client = mock(S3Client.class);
        ListObjectsV2Response firstPage = ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("customer/a.txt").size(12L).lastModified(Instant.parse("2026-01-01T00:00:00Z")).build(),
                        S3Object.builder().key("customer/b.txt").size(8L).lastModified(Instant.parse("2026-01-02T00:00:00Z")).build()
                )
                .isTruncated(true)
                .nextContinuationToken("next-token")
                .build();
        ListObjectsV2Response secondPage = ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("customer/c.txt").size(3L).lastModified(Instant.parse("2026-01-03T00:00:00Z")).build()
                )
                .isTruncated(false)
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(firstPage, secondPage);

        S3ProviderClient client = new S3ProviderClient(s3Client);
        List<StorageObjectInfo> result = client.listFiles("docs", new ListFilesRequest(null, null));

        assertEquals(3, result.size());
        assertEquals("customer/c.txt", result.get(2).key());
        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void shouldCloseClient() {
        S3Client s3Client = mock(S3Client.class);
        S3ProviderClient client = new S3ProviderClient(s3Client);

        client.close();

        verify(s3Client).close();
    }

    @Test
    void shouldReturnProvider() {
        S3ProviderClient client = new S3ProviderClient(mock(S3Client.class));

        assertSame(StorageProvider.S3, client.provider());
    }

    private static UploadFileRequest uploadRequest() {
        return new UploadFileRequest(
                "customer/a.txt",
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3L,
                "text/plain",
                Map.of("owner", "team")
        );
    }
}
