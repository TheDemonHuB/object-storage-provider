package com.example.objectstorage.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.objectstorage.api.StorageProvider;
import com.example.objectstorage.api.request.CopyFileRequest;
import com.example.objectstorage.api.request.DeleteFileRequest;
import com.example.objectstorage.api.request.GetFileRequest;
import com.example.objectstorage.api.request.ListFilesRequest;
import com.example.objectstorage.api.request.MoveFileRequest;
import com.example.objectstorage.api.request.UploadFileRequest;
import com.example.objectstorage.api.response.DeletedObject;
import com.example.objectstorage.api.response.RetrievedObject;
import com.example.objectstorage.api.response.StorageObjectInfo;
import com.example.objectstorage.api.response.StoredObject;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultObjectStorageServiceTest {
    private static final ZoneId TEST_ZONE = ZoneId.of("UTC");
    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-04-26T09:15:30.123Z"), TEST_ZONE);
    private static final String TEST_BASE_PATH = "documents";
    private static final String TEST_PREFIX = "documents/2026/04/26/091530123";
    private static final String TEST_BUCKET = "bucket";

    @Test
    void shouldRouteCallsToConfiguredProvider() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET
        );

        RetrievedObject object = service.getFiles(List.of(new GetFileRequest("key"))).getFirst();
        assertEquals("key", object.key());
        assertEquals(StorageProvider.S3, object.provider());
    }

    @Test
    void shouldSaveFilesInInputOrderAcrossConfiguredBatches() {
        DefaultObjectStorageService service = createService(new StubProviderClient(StorageProvider.S3), 2, 2, 5);

        List<StoredObject> storedObjects = service.saveFiles(List.of(
                uploadRequest("first.txt"),
                uploadRequest("second.txt"),
                uploadRequest("third.txt")
        ));

        assertEquals(List.of(
                TEST_PREFIX + "/first.txt",
                TEST_PREFIX + "/second.txt",
                TEST_PREFIX + "/third.txt"
        ), storedObjects.stream()
                .map(StoredObject::key)
                .toList());
        assertEquals(List.of("first.txt", "second.txt", "third.txt"), storedObjects.stream()
                .map(StoredObject::originalKey)
                .toList());
    }

    @Test
    void shouldGetFilesInInputOrderAcrossConfiguredBatches() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                2,
                2,
                5
        );

        List<RetrievedObject> objects = service.getFiles(List.of(
                new GetFileRequest("first.txt"),
                new GetFileRequest("second.txt"),
                new GetFileRequest("third.txt")
        ));

        assertEquals(List.of("first.txt", "second.txt", "third.txt"), objects.stream()
                .map(RetrievedObject::key)
                .toList());
    }

    @Test
    void shouldUseRequestProviderAndBucketOverrideForGet() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(
                        StorageProvider.S3, new StubProviderClient(StorageProvider.S3),
                        StorageProvider.GCP, new StubProviderClient(StorageProvider.GCP)
                ),
                StorageProvider.S3,
                TEST_BUCKET
        );

        RetrievedObject object = service.getFiles(List.of(new GetFileRequest("key", StorageProvider.GCP, "custom-bucket"))).getFirst();

        assertEquals(StorageProvider.GCP, object.provider());
        assertEquals("custom-bucket", object.bucket());
    }

    @Test
    void shouldUseRequestProviderAndBucketOverrideForDelete() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(
                        StorageProvider.S3, new StubProviderClient(StorageProvider.S3),
                        StorageProvider.GCP, new StubProviderClient(StorageProvider.GCP)
                ),
                StorageProvider.S3,
                TEST_BUCKET
        );

        DeletedObject object = service.deleteFiles(
                List.of(new DeleteFileRequest("key", StorageProvider.GCP, "custom-bucket"))
        ).getFirst();

        assertEquals(StorageProvider.GCP, object.provider());
        assertEquals("custom-bucket", object.bucket());
    }

    @Test
    void shouldUseRequestProviderAndBucketOverrideForList() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(
                        StorageProvider.S3, new StubProviderClient(StorageProvider.S3),
                        StorageProvider.GCP, new StubProviderClient(StorageProvider.GCP)
                ),
                StorageProvider.S3,
                TEST_BUCKET
        );

        List<StorageObjectInfo> objects = service.listFiles(new ListFilesRequest("docs/", null, StorageProvider.GCP, "custom-bucket"));

        assertEquals(1, objects.size());
    }

    @Test
    void shouldDeleteFilesAndReturnDeletedObjectsInInputOrder() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                2,
                2,
                5
        );

        List<DeletedObject> deletedObjects = service.deleteFiles(List.of(
                new DeleteFileRequest("first.txt"),
                new DeleteFileRequest("second.txt")
        ));

        assertEquals(List.of("first.txt", "second.txt"), deletedObjects.stream()
                .map(DeletedObject::key)
                .toList());
    }

    @Test
    void shouldCopyFilesAcrossProvidersUsingRequestOverrides() {
        TrackingCopyMoveProviderClient s3Provider = new TrackingCopyMoveProviderClient(StorageProvider.S3);
        TrackingCopyMoveProviderClient gcpProvider = new TrackingCopyMoveProviderClient(StorageProvider.GCP);
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, s3Provider, StorageProvider.GCP, gcpProvider),
                StorageProvider.S3,
                TEST_BUCKET
        );

        StoredObject copied = service.copyFiles(List.of(new CopyFileRequest(
                "source/report.pdf",
                "target/report.pdf",
                StorageProvider.S3,
                "source-bucket",
                StorageProvider.GCP,
                "target-bucket"
        ))).getFirst();

        assertEquals(StorageProvider.GCP, copied.provider());
        assertEquals("target-bucket", copied.bucket());
        assertEquals("source/report.pdf", copied.originalKey());
        assertEquals("target/report.pdf", copied.key());
        assertEquals("source/report.pdf", s3Provider.requestedGetKeys().getFirst());
        assertEquals("target/report.pdf", gcpProvider.savedKeys().getFirst());
        assertEquals(List.of(), s3Provider.deletedKeys());
    }

    @Test
    void shouldMoveFilesByCopyingThenDeletingOriginal() {
        TrackingCopyMoveProviderClient s3Provider = new TrackingCopyMoveProviderClient(StorageProvider.S3);
        TrackingCopyMoveProviderClient azureProvider = new TrackingCopyMoveProviderClient(StorageProvider.AZURE);
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, s3Provider, StorageProvider.AZURE, azureProvider),
                StorageProvider.S3,
                TEST_BUCKET
        );

        StoredObject moved = service.moveFiles(List.of(new MoveFileRequest(
                "source/archive.zip",
                "target/archive.zip",
                StorageProvider.S3,
                "source-bucket",
                StorageProvider.AZURE,
                "target-bucket"
        ))).getFirst();

        assertEquals(StorageProvider.AZURE, moved.provider());
        assertEquals("target-bucket", moved.bucket());
        assertEquals("source/archive.zip", moved.originalKey());
        assertEquals("target/archive.zip", moved.key());
        assertEquals("source/archive.zip", s3Provider.requestedGetKeys().getFirst());
        assertEquals("source/archive.zip", s3Provider.deletedKeys().getFirst());
        assertEquals("target/archive.zip", azureProvider.savedKeys().getFirst());
    }

    @Test
    void shouldRollbackCopiedTargetWhenMoveSourceDeleteFails() {
        FailingDeleteCopyMoveProviderClient s3Provider = new FailingDeleteCopyMoveProviderClient(StorageProvider.S3);
        TrackingCopyMoveProviderClient azureProvider = new TrackingCopyMoveProviderClient(StorageProvider.AZURE);
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, s3Provider, StorageProvider.AZURE, azureProvider),
                StorageProvider.S3,
                TEST_BUCKET
        );

        ObjectStorageException ex = assertThrows(
                ObjectStorageException.class,
                () -> service.moveFiles(List.of(new MoveFileRequest(
                        "source/archive.zip",
                        "target/archive.zip",
                        StorageProvider.S3,
                        "source-bucket",
                        StorageProvider.AZURE,
                        "target-bucket"
                )))
        );

        assertTrue(ex.getMessage().contains("Batch move failed"));
        assertTrue(ex.getCause().getMessage().contains("Move failed after copy"));
        assertEquals("source/archive.zip", s3Provider.deletedKeys().getFirst());
        assertEquals("target/archive.zip", azureProvider.savedKeys().getFirst());
        assertEquals("target/archive.zip", azureProvider.deletedKeys().getFirst());
    }

    @Test
    void shouldApplyDefaultListMaxResultsWhenRequestMaxResultsMissing() {
        TrackingListProviderClient provider = new TrackingListProviderClient(StorageProvider.S3);
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, provider),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                new DefaultObjectStorageService.ServiceSettings(
                        100,
                        4,
                        500,
                        2,
                        null,
                        null,
                        TEST_BASE_PATH,
                        TEST_ZONE,
                        TEST_CLOCK
                )
        );

        service.listFiles(new ListFilesRequest("docs/", null));

        assertEquals(2, provider.lastListRequest().maxResults());
        assertEquals("docs/", provider.lastListRequest().prefix());
    }

    @Test
    void shouldPreferRequestMaxResultsOverDefaultListMaxResults() {
        TrackingListProviderClient provider = new TrackingListProviderClient(StorageProvider.S3);
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, provider),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                new DefaultObjectStorageService.ServiceSettings(
                        100,
                        4,
                        500,
                        5,
                        null,
                        null,
                        TEST_BASE_PATH,
                        TEST_ZONE,
                        TEST_CLOCK
                )
        );

        service.listFiles(new ListFilesRequest("docs/", 3));

        assertEquals(3, provider.lastListRequest().maxResults());
    }

    @Test
    void shouldKeepListUnlimitedWhenDefaultAndRequestMaxResultsAreNull() {
        TrackingListProviderClient provider = new TrackingListProviderClient(StorageProvider.S3);
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, provider),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                new DefaultObjectStorageService.ServiceSettings(
                        100,
                        4,
                        500,
                        null,
                        null,
                        null,
                        TEST_BASE_PATH,
                        TEST_ZONE,
                        TEST_CLOCK
                )
        );

        service.listFiles(new ListFilesRequest("docs/", null));

        assertNull(provider.lastListRequest().maxResults());
    }

    @Test
    void shouldRenameDuplicateFilenamesWithinSameSaveCall() {
        DefaultObjectStorageService service = createService(new StubProviderClient(StorageProvider.S3), 5, 2, 5);

        List<StoredObject> storedObjects = service.saveFiles(List.of(
                uploadRequest("customer-123/invoice.pdf"),
                uploadRequest("customer-123/invoice.pdf"),
                uploadRequest("customer-123/invoice.pdf")
        ));

        assertEquals(List.of(
                TEST_PREFIX + "/customer-123/invoice.pdf",
                TEST_PREFIX + "/customer-123/1_invoice.pdf",
                TEST_PREFIX + "/customer-123/2_invoice.pdf"
        ), storedObjects.stream().map(StoredObject::key).toList());
        assertEquals(List.of(
                "customer-123/invoice.pdf",
                "customer-123/invoice.pdf",
                "customer-123/invoice.pdf"
        ), storedObjects.stream().map(StoredObject::originalKey).toList());
        assertEquals(List.of("invoice.pdf", "1_invoice.pdf", "2_invoice.pdf"), storedObjects.stream()
                .map(StoredObject::storedFilename)
                .toList());
    }

    @Test
    void shouldRejectSaveWhenExtensionIsNotAllowed() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                new DefaultObjectStorageService.ServiceSettings(
                        100,
                        4,
                        500,
                        null,
                        Set.of("pdf"),
                        null,
                        TEST_BASE_PATH,
                        TEST_ZONE,
                        TEST_CLOCK
                )
        );
        UploadFileRequest disallowedRequest = uploadRequest("note.txt");
        List<UploadFileRequest> saveRequests = List.of(disallowedRequest);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveFiles(saveRequests)
        );

        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void shouldRejectSaveWhenFileSizeExceedsConfiguredMax() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                new DefaultObjectStorageService.ServiceSettings(
                        100,
                        4,
                        500,
                        null,
                        null,
                        1L,
                        TEST_BASE_PATH,
                        TEST_ZONE,
                        TEST_CLOCK
                )
        );
        UploadFileRequest oversizedRequest = new UploadFileRequest(
                "large.bin",
                new ByteArrayInputStream(new byte[]{1, 2}),
                2L,
                "application/octet-stream",
                Map.of()
        );
        List<UploadFileRequest> saveRequests = List.of(oversizedRequest);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveFiles(saveRequests)
        );

        assertTrue(ex.getMessage().contains("exceeds maxFileSizeBytes"));
    }

    @Test
    void shouldReturnEmptyListForEmptyBatch() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET
        );

        assertEquals(List.of(), service.saveFiles(List.of()));
        assertEquals(List.of(), service.getFiles(List.of()));
        assertEquals(List.of(), service.deleteFiles(List.of()));
    }

    @Test
    void shouldRejectBatchThatExceedsConfiguredMaxBatchItems() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                2,
                2,
                2
        );
        List<UploadFileRequest> requests = List.of(
                uploadRequest("first.txt"),
                uploadRequest("second.txt"),
                uploadRequest("third.txt")
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.saveFiles(requests));

        assertEquals("save batch contains 3 items, which exceeds configured maxBatchItems=2", ex.getMessage());
    }

    @Test
    void shouldRejectNullBatchItems() {
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET
        );
        List<GetFileRequest> requests = java.util.Arrays.asList(
                new GetFileRequest("first.txt"),
                null
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.getFiles(requests)
        );

        assertEquals("get request at index 1 must not be null", ex.getMessage());
    }

    @Test
    void shouldRollbackSavedFilesWhenSaveBatchFails() {
        FailingSaveProviderClient provider = new FailingSaveProviderClient(StorageProvider.S3, "fail.txt");
        DefaultObjectStorageService service = createService(provider, 3, 1, 5);
        List<UploadFileRequest> requests = List.of(
                uploadRequest("first.txt"),
                uploadRequest("fail.txt"),
                uploadRequest("third.txt")
        );

        ObjectStorageException ex = assertThrows(ObjectStorageException.class, () -> service.saveFiles(requests));

        assertTrue(ex.getMessage().contains("Batch save failed at index 1"));
        assertEquals(1, provider.savedKeys().size());
        assertEquals(provider.savedKeys(), provider.deletedKeys());
        assertTrue(provider.savedKeys().get(0).endsWith("/first.txt"));
    }

    @Test
    void shouldProcessBatchItemsInParallelWithinConfiguredConcurrency() throws Exception {
        TrackingStubProviderClient provider = new TrackingStubProviderClient(StorageProvider.S3);
        DefaultObjectStorageService service = new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, provider),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                4,
                2,
                4
        );
        List<GetFileRequest> requests = List.of(
                new GetFileRequest("first.txt"),
                new GetFileRequest("second.txt"),
                new GetFileRequest("third.txt"),
                new GetFileRequest("fourth.txt")
        );

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<List<RetrievedObject>> future = executorService.submit(() -> service.getFiles(requests));
            assertTrue(provider.awaitGetEntries());
            assertEquals(2, provider.maxConcurrentGetsObserved());
            provider.releaseGets();
            future.get(5, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldFailWhenDefaultProviderNotConfigured() {
        assertThrows(IllegalArgumentException.class, this::createServiceWithUnconfiguredDefaultProvider);
    }

    @Test
    void shouldRejectNegativeConcurrentSaveLimit() {
        assertThrows(IllegalArgumentException.class, this::createServiceWithNegativeConcurrentLimit);
    }

    @Test
    void shouldRejectInvalidBatchConfiguration() {
        assertThrows(IllegalArgumentException.class, this::createServiceWithZeroBatchSize);
        assertThrows(IllegalArgumentException.class, this::createServiceWithZeroConcurrentBatchItems);
        assertThrows(IllegalArgumentException.class, this::createServiceWithZeroMaxBatchItems);
    }

    @Test
    void shouldRejectMissingDefaultProviderAndBucket() {
        assertThrows(NullPointerException.class, this::createServiceWithNullDefaultProvider);
        assertThrows(IllegalArgumentException.class, this::createServiceWithBlankDefaultBucket);
    }

    @Test
    void shouldSerializeSavesWhenSingleConcurrencyIsConfigured() throws Exception {
        TrackingStubProviderClient provider = new TrackingStubProviderClient(StorageProvider.S3);
        DefaultObjectStorageService service = createService(provider, 100, 4, 500, 1);

        UploadFileRequest firstRequest = new UploadFileRequest(
                "first.txt",
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "application/octet-stream",
                Map.of()
        );
        UploadFileRequest secondRequest = new UploadFileRequest(
                "second.txt",
                new ByteArrayInputStream(new byte[]{2}),
                1L,
                "application/octet-stream",
                Map.of()
        );

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);

            Future<?> firstFuture = executorService.submit(() -> {
                readyLatch.countDown();
                if (!startLatch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for start signal");
                }
                return service.saveFiles(List.of(firstRequest)).getFirst();
            });
            Future<?> secondFuture = executorService.submit(() -> {
                readyLatch.countDown();
                if (!startLatch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for start signal");
                }
                return service.saveFiles(List.of(secondRequest)).getFirst();
            });

            assertTrue(readyLatch.await(2, TimeUnit.SECONDS));
            startLatch.countDown();

            assertTrue(provider.awaitSaveEntries());
            provider.releaseSaves();
            firstFuture.get(5, TimeUnit.SECONDS);
            secondFuture.get(5, TimeUnit.SECONDS);
            assertEquals(1, provider.maxConcurrentSavesObserved());
        } finally {
            executorService.shutdownNow();
        }
    }

    private UploadFileRequest uploadRequest(String key) {
        return new UploadFileRequest(
                key,
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "application/octet-stream",
                Map.of()
        );
    }

    private DefaultObjectStorageService createService(
            StubProviderClient provider,
            int batchSize,
            int maxConcurrentBatchItems,
            int maxBatchItems
    ) {
        return createService(provider, batchSize, maxConcurrentBatchItems, maxBatchItems, 0);
    }

    private DefaultObjectStorageService createService(
            StubProviderClient provider,
            int batchSize,
            int maxConcurrentBatchItems,
            int maxBatchItems,
            int maxConcurrentSaves
    ) {
        return new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, provider),
                StorageProvider.S3,
                TEST_BUCKET,
                maxConcurrentSaves,
                new DefaultObjectStorageService.ServiceSettings(
                        batchSize,
                        maxConcurrentBatchItems,
                        maxBatchItems,
                        null,
                        null,
                        null,
                        TEST_BASE_PATH,
                        TEST_ZONE,
                        TEST_CLOCK
                )
        );
    }

    private DefaultObjectStorageService createServiceWithNegativeConcurrentLimit() {
        return new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET,
                -1
        );
    }

    private DefaultObjectStorageService createServiceWithZeroBatchSize() {
        return new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                new DefaultObjectStorageService.ServiceSettings(0, 1, 500, null, null, null, null, TEST_ZONE, TEST_CLOCK)
        );
    }

    private DefaultObjectStorageService createServiceWithZeroConcurrentBatchItems() {
        return new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                new DefaultObjectStorageService.ServiceSettings(100, 0, 500, null, null, null, null, TEST_ZONE, TEST_CLOCK)
        );
    }

    private DefaultObjectStorageService createServiceWithZeroMaxBatchItems() {
        return new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                TEST_BUCKET,
                0,
                new DefaultObjectStorageService.ServiceSettings(100, 1, 0, null, null, null, null, TEST_ZONE, TEST_CLOCK)
        );
    }

    private DefaultObjectStorageService createServiceWithUnconfiguredDefaultProvider() {
        return new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.GCP,
                TEST_BUCKET
        );
    }

    private DefaultObjectStorageService createServiceWithNullDefaultProvider() {
        return new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                null,
                TEST_BUCKET
        );
    }

    private DefaultObjectStorageService createServiceWithBlankDefaultBucket() {
        return new DefaultObjectStorageService(
                Map.of(StorageProvider.S3, new StubProviderClient(StorageProvider.S3)),
                StorageProvider.S3,
                " "
        );
    }

    private static final class TrackingStubProviderClient extends StubProviderClient {
        private final AtomicInteger activeSaves = new AtomicInteger();
        private final AtomicInteger activeGets = new AtomicInteger();
        private final AtomicInteger maxObserved = new AtomicInteger();
        private final AtomicInteger maxGetsObserved = new AtomicInteger();
        private final CountDownLatch saveEntryLatch = new CountDownLatch(1);
        private final CountDownLatch getEntryLatch = new CountDownLatch(2);
        private final CountDownLatch releaseSavesLatch = new CountDownLatch(1);
        private final CountDownLatch releaseGetsLatch = new CountDownLatch(1);

        private TrackingStubProviderClient(StorageProvider provider) {
            super(provider);
        }

        @Override
        public StoredObject saveFile(String bucket, UploadFileRequest request) {
            int active = activeSaves.incrementAndGet();
            maxObserved.accumulateAndGet(active, Math::max);
            try {
                saveEntryLatch.countDown();
                awaitLatch(releaseSavesLatch, "save release");
                return super.saveFile(bucket, request);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ObjectStorageException("Interrupted during simulated save", ex);
            } finally {
                activeSaves.decrementAndGet();
            }
        }

        @Override
        public RetrievedObject getFile(String bucket, GetFileRequest request) {
            int active = activeGets.incrementAndGet();
            maxGetsObserved.accumulateAndGet(active, Math::max);
            try {
                getEntryLatch.countDown();
                awaitLatch(releaseGetsLatch, "get release");
                return super.getFile(bucket, request);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ObjectStorageException("Interrupted during simulated get", ex);
            } finally {
                activeGets.decrementAndGet();
            }
        }

        private int maxConcurrentSavesObserved() {
            return maxObserved.get();
        }

        private int maxConcurrentGetsObserved() {
            return maxGetsObserved.get();
        }

        private boolean awaitSaveEntries() throws InterruptedException {
            return saveEntryLatch.await(2, TimeUnit.SECONDS);
        }

        private boolean awaitGetEntries() throws InterruptedException {
            return getEntryLatch.await(2, TimeUnit.SECONDS);
        }

        private void releaseSaves() {
            releaseSavesLatch.countDown();
        }

        private void releaseGets() {
            releaseGetsLatch.countDown();
        }

        private void awaitLatch(CountDownLatch latch, String operation) throws InterruptedException {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for " + operation);
            }
        }
    }

    private static final class FailingSaveProviderClient extends StubProviderClient {
        private final String failingKey;
        private final List<String> savedKeys = new CopyOnWriteArrayList<>();
        private final List<String> deletedKeys = new CopyOnWriteArrayList<>();

        private FailingSaveProviderClient(StorageProvider provider, String failingKey) {
            super(provider);
            this.failingKey = failingKey;
        }

        @Override
        public StoredObject saveFile(String bucket, UploadFileRequest request) {
            if (request.key().endsWith("/" + failingKey) || request.key().equals(failingKey)) {
                throw new ObjectStorageException("Simulated save failure");
            }
            savedKeys.add(request.key());
            return super.saveFile(bucket, request);
        }

        @Override
        public void deleteFile(String bucket, DeleteFileRequest request) {
            deletedKeys.add(request.key());
        }

        private List<String> savedKeys() {
            return List.copyOf(savedKeys);
        }

        private List<String> deletedKeys() {
            return List.copyOf(deletedKeys);
        }
    }

    private static final class TrackingListProviderClient extends StubProviderClient {
        private ListFilesRequest lastListRequest;

        private TrackingListProviderClient(StorageProvider provider) {
            super(provider);
        }

        @Override
        public List<StorageObjectInfo> listFiles(String bucket, ListFilesRequest request) {
            this.lastListRequest = request;
            return List.of();
        }

        private ListFilesRequest lastListRequest() {
            return lastListRequest;
        }
    }

    private static final class TrackingCopyMoveProviderClient extends StubProviderClient {
        private final List<String> requestedGetKeys = new CopyOnWriteArrayList<>();
        private final List<String> savedKeys = new CopyOnWriteArrayList<>();
        private final List<String> deletedKeys = new CopyOnWriteArrayList<>();

        private TrackingCopyMoveProviderClient(StorageProvider provider) {
            super(provider);
        }

        @Override
        public RetrievedObject getFile(String bucket, GetFileRequest request) {
            requestedGetKeys.add(request.key());
            return new RetrievedObject(
                    provider(),
                    bucket,
                    request.key(),
                    new ByteArrayInputStream(new byte[]{1, 2, 3}),
                    "application/octet-stream",
                    Map.of("copied-from", request.key()),
                    3L
            );
        }

        @Override
        public StoredObject saveFile(String bucket, UploadFileRequest request) {
            savedKeys.add(request.key());
            return new StoredObject(provider(), bucket, request.key(), "etag-copy", null);
        }

        @Override
        public void deleteFile(String bucket, DeleteFileRequest request) {
            deletedKeys.add(request.key());
        }

        private List<String> requestedGetKeys() {
            return List.copyOf(requestedGetKeys);
        }

        private List<String> savedKeys() {
            return List.copyOf(savedKeys);
        }

        private List<String> deletedKeys() {
            return List.copyOf(deletedKeys);
        }
    }

    private static final class FailingDeleteCopyMoveProviderClient extends StubProviderClient {
        private final List<String> requestedGetKeys = new CopyOnWriteArrayList<>();
        private final List<String> deletedKeys = new CopyOnWriteArrayList<>();

        private FailingDeleteCopyMoveProviderClient(StorageProvider provider) {
            super(provider);
        }

        @Override
        public RetrievedObject getFile(String bucket, GetFileRequest request) {
            requestedGetKeys.add(request.key());
            return new RetrievedObject(
                    provider(),
                    bucket,
                    request.key(),
                    new ByteArrayInputStream(new byte[]{1, 2, 3}),
                    "application/octet-stream",
                    Map.of("copied-from", request.key()),
                    3L
            );
        }

        @Override
        public void deleteFile(String bucket, DeleteFileRequest request) {
            deletedKeys.add(request.key());
            throw new ObjectStorageException("Simulated source delete failure");
        }

        private List<String> deletedKeys() {
            return List.copyOf(deletedKeys);
        }

        @SuppressWarnings("unused")
        private List<String> requestedGetKeys() {
            return List.copyOf(requestedGetKeys);
        }
    }
}
