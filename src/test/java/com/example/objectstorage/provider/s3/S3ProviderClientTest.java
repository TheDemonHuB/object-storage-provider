package com.example.objectstorage.provider.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.example.objectstorage.core.ObjectStorageException;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3ProviderClientTest {
    @Test
    void shouldSaveFile() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(ObjectVersion.builder().key("customer/a.txt").versionId("v1").build())
                        .isTruncated(false)
                        .build()
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").versionId("v1").build());

        S3ProviderClient client = new S3ProviderClient(s3Client);
        StoredObject result = client.saveFile("docs", uploadRequest());

        assertEquals("etag", result.eTag());
        assertEquals("v1", result.versionId());
    }

    @Test
    void shouldFailSaveWhenExpectedVersionDoesNotMatchCurrentVersion() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(ObjectVersion.builder().key("customer/a.txt").versionId("v2").build())
                        .isTruncated(false)
                        .build()
        );
        S3ProviderClient client = new S3ProviderClient(s3Client);

        assertThrows(
                ObjectStorageException.class,
                () -> client.saveFile("docs", new UploadFileRequest(
                        "customer/a.txt",
                        new ByteArrayInputStream(new byte[]{1, 2, 3}),
                        3L,
                        "text/plain",
                        Map.of(),
                        "v1",
                        null,
                        null
                ))
        );
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void shouldTreatLatestDeleteMarkerAsCurrentVersionForSavePrecondition() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(ObjectVersion.builder()
                                .key("customer/a.txt")
                                .versionId("v1")
                                .isLatest(false)
                                .build())
                        .deleteMarkers(DeleteMarkerEntry.builder()
                                .key("customer/a.txt")
                                .versionId("delete-v2")
                                .isLatest(true)
                                .build())
                        .isTruncated(false)
                        .build()
        );
        S3ProviderClient client = new S3ProviderClient(s3Client);

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
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void shouldSaveWhenExpectedVersionMatchesLatestDeleteMarker() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(ObjectVersion.builder()
                                .key("customer/a.txt")
                                .versionId("v1")
                                .isLatest(false)
                                .build())
                        .deleteMarkers(DeleteMarkerEntry.builder()
                                .key("customer/a.txt")
                                .versionId("delete-v2")
                                .isLatest(true)
                                .build())
                        .isTruncated(false)
                        .build()
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").versionId("v3").build());
        S3ProviderClient client = new S3ProviderClient(s3Client);

        StoredObject result = client.saveFile("docs", new UploadFileRequest(
                "customer/a.txt",
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "text/plain",
                Map.of(),
                "delete-v2",
                null,
                null
        ));

        assertEquals("v3", result.versionId());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void shouldSaveWhenExpectedVersionDoesNotMatchAndOverrideIsEnabled() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(ObjectVersion.builder().key("customer/a.txt").versionId("v2").build())
                        .isTruncated(false)
                        .build()
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").versionId("v3").build());

        S3ProviderClient client = new S3ProviderClient(s3Client, true);
        StoredObject result = client.saveFile("docs", new UploadFileRequest(
                "customer/a.txt",
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3L,
                "text/plain",
                Map.of(),
                "v1",
                null,
                null
        ));

        assertEquals("v3", result.versionId());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
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
        RetrievedObject result = client.getFile("docs", new GetFileRequest("customer/a.txt", null, null, null));

        assertEquals(4L, result.size());
        assertEquals("text/plain", result.contentType());
        assertEquals("team", result.metadata().get("owner"));
    }

    @Test
    void shouldDeleteFile() {
        S3Client s3Client = mock(S3Client.class);
        S3ProviderClient client = new S3ProviderClient(s3Client);

        client.deleteFile("docs", new DeleteFileRequest("customer/a.txt", null, null, null));

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void shouldSupportVersionedGetAndDeleteRequests() {
        S3Client s3Client = mock(S3Client.class);
        GetObjectResponse response = GetObjectResponse.builder().contentLength(1L).build();
        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> stream = mock(ResponseInputStream.class);
        when(stream.response()).thenReturn(response);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);
        S3ProviderClient client = new S3ProviderClient(s3Client);

        client.getFile("docs", new GetFileRequest("customer/a.txt", "v5", null, null));
        client.deleteFile("docs", new DeleteFileRequest("customer/a.txt", "v5", null, null));

        verify(s3Client).getObject(any(GetObjectRequest.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void shouldFailSaveWhenExpectedVersionIsProvidedButNoCurrentVersionExists() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(List.of())
                        .isTruncated(false)
                        .build()
        );
        S3ProviderClient client = new S3ProviderClient(s3Client);

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
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void shouldSaveWhenCurrentVersionIsMissingAndOverrideIsEnabled() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(List.of())
                        .isTruncated(false)
                        .build()
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").versionId("v1").build());
        S3ProviderClient client = new S3ProviderClient(s3Client, true);

        StoredObject result = client.saveFile("docs", new UploadFileRequest(
                "customer/a.txt",
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "text/plain",
                Map.of(),
                "v1",
                null,
                null
        ));

        assertEquals("v1", result.versionId());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void shouldListFiles() {
        S3Client s3Client = mock(S3Client.class);
        ListObjectVersionsResponse response = ListObjectVersionsResponse.builder()
                .versions(
                        ObjectVersion.builder().key("customer/").size(0L).lastModified(Instant.parse("2026-01-01T00:00:00Z")).versionId("v0").build(),
                        ObjectVersion.builder().key("customer/a.txt").size(12L).lastModified(Instant.parse("2026-01-01T00:00:00Z")).versionId("v1").build(),
                        ObjectVersion.builder().key("customer/b.txt").size(8L).lastModified(Instant.parse("2026-01-02T00:00:00Z")).versionId("v2").build()
                )
                .build();
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(response);

        S3ProviderClient client = new S3ProviderClient(s3Client);
        List<StorageObjectInfo> result = client.listFiles("docs", new ListFilesRequest("customer/", 10));

        assertEquals(2, result.size());
        assertEquals("customer/a.txt", result.getFirst().filePath());
        assertEquals("v1", result.getFirst().versionId());
        assertEquals(8L, result.get(1).size());
    }

    @Test
    void shouldListFilesAcrossPagesWhenMaxResultsNotProvided() {
        S3Client s3Client = mock(S3Client.class);
        ListObjectVersionsResponse firstPage = ListObjectVersionsResponse.builder()
                .versions(
                        ObjectVersion.builder().key("customer/a.txt").size(12L).lastModified(Instant.parse("2026-01-01T00:00:00Z")).versionId("v1").build(),
                        ObjectVersion.builder().key("customer/b.txt").size(8L).lastModified(Instant.parse("2026-01-02T00:00:00Z")).versionId("v2").build()
                )
                .isTruncated(true)
                .nextKeyMarker("next-key")
                .nextVersionIdMarker("next-version")
                .build();
        ListObjectVersionsResponse secondPage = ListObjectVersionsResponse.builder()
                .versions(
                        ObjectVersion.builder().key("customer/c.txt").size(3L).lastModified(Instant.parse("2026-01-03T00:00:00Z")).versionId("v3").build()
                )
                .isTruncated(false)
                .build();
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(firstPage, secondPage);

        S3ProviderClient client = new S3ProviderClient(s3Client);
        List<StorageObjectInfo> result = client.listFiles("docs", new ListFilesRequest(null, null));

        assertEquals(3, result.size());
        assertEquals("customer/c.txt", result.get(2).filePath());
        assertEquals("v3", result.get(2).versionId());
        verify(s3Client, times(2)).listObjectVersions(any(ListObjectVersionsRequest.class));
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
                Map.of("owner", "team"),
                "v1",
                null,
                null
        );
    }
}

