package com.example.objectstorage.core;

import com.example.objectstorage.api.ObjectStorageService;
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
import com.example.objectstorage.config.ObjectStorageServiceBuilder;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default orchestration layer that routes calls to provider clients and applies
 * batching, concurrency limits, request validation, and transactional rollback for save operations.
 */
public final class DefaultObjectStorageService implements ObjectStorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultObjectStorageService.class);
    private static final String BATCH_PREFIX = "Batch ";
    private static final DateTimeFormatter SAVE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd/HHmmssSSS");

    private final Map<StorageProvider, ProviderClient> providers;
    private final StorageProvider defaultProvider;
    private final String defaultBucket;
    private final Semaphore saveSemaphore;
    private final int batchSize;
    private final int maxConcurrentBatchItems;
    private final int maxBatchItems;
    private final Integer defaultListMaxResults;
    private final Set<String> allowedFileExtensions;
    private final Long maxFileSizeBytes;
    private final String basePath;
    private final ZoneId timeZone;
    private final Clock clock;

    public DefaultObjectStorageService(
            Map<StorageProvider, ProviderClient> providers,
            StorageProvider defaultProvider,
            String defaultBucket
    ) {
        this(providers, defaultProvider, defaultBucket, 0);
    }

    public DefaultObjectStorageService(
            Map<StorageProvider, ProviderClient> providers,
            StorageProvider defaultProvider,
            String defaultBucket,
            int maxConcurrentSaves
    ) {
        this(
                providers,
                defaultProvider,
                defaultBucket,
                maxConcurrentSaves,
                ServiceSettings.defaultSettings()
        );
    }

    public DefaultObjectStorageService(
            Map<StorageProvider, ProviderClient> providers,
            StorageProvider defaultProvider,
            String defaultBucket,
            int maxConcurrentSaves,
            int batchSize,
            int maxConcurrentBatchItems,
            int maxBatchItems
    ) {
        this(
                providers,
                defaultProvider,
                defaultBucket,
                maxConcurrentSaves,
                new ServiceSettings(
                        batchSize,
                        maxConcurrentBatchItems,
                        maxBatchItems,
                        ObjectStorageServiceBuilder.DEFAULT_LIST_MAX_RESULTS,
                        null,
                        null,
                        null,
                        ObjectStorageServiceBuilder.DEFAULT_TIME_ZONE,
                        Clock.system(ObjectStorageServiceBuilder.DEFAULT_TIME_ZONE)
                )
        );
    }

    public DefaultObjectStorageService(
            Map<StorageProvider, ProviderClient> providers,
            StorageProvider defaultProvider,
            String defaultBucket,
            int maxConcurrentSaves,
            ServiceSettings settings
    ) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        if (maxConcurrentSaves < 0) {
            throw new IllegalArgumentException("maxConcurrentSaves must not be negative");
        }
        Objects.requireNonNull(settings, "settings must not be null");
        if (settings.batchSize() <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        if (settings.maxConcurrentBatchItems() <= 0) {
            throw new IllegalArgumentException("maxConcurrentBatchItems must be greater than 0");
        }
        if (settings.maxBatchItems() <= 0) {
            throw new IllegalArgumentException("maxBatchItems must be greater than 0");
        }
        if (settings.defaultListMaxResults() != null && settings.defaultListMaxResults() <= 0) {
            throw new IllegalArgumentException("defaultListMaxResults must be greater than 0");
        }
        if (settings.maxFileSizeBytes() != null && settings.maxFileSizeBytes() <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must be greater than 0");
        }
        this.defaultProvider = Objects.requireNonNull(defaultProvider, "defaultProvider must not be null");
        this.defaultBucket = ValidationUtils.requireNonBlank(defaultBucket, "defaultBucket");
        if (!providers.containsKey(defaultProvider)) {
            throw new IllegalArgumentException("defaultProvider is not configured: " + defaultProvider);
        }
        this.providers = Map.copyOf(new EnumMap<>(providers));
        this.saveSemaphore = maxConcurrentSaves == 0 ? null : new Semaphore(maxConcurrentSaves, true);
        this.batchSize = settings.batchSize();
        this.maxConcurrentBatchItems = settings.maxConcurrentBatchItems();
        this.maxBatchItems = settings.maxBatchItems();
        this.defaultListMaxResults = settings.defaultListMaxResults();
        this.allowedFileExtensions = settings.allowedFileExtensions() == null
                ? null
                : Set.copyOf(settings.allowedFileExtensions());
        this.maxFileSizeBytes = settings.maxFileSizeBytes();
        this.basePath = normalizePath(settings.basePath());
        this.timeZone = Objects.requireNonNull(settings.timeZone(), "timeZone must not be null");
        this.clock = Objects.requireNonNull(settings.clock(), "clock must not be null");
    }

    @Override
    public List<StoredObject> saveFiles(List<UploadFileRequest> requests) {
        List<UploadFileRequest> validatedRequests = validateBatchRequests(requests, "save");
        List<ResolvedSaveRequest> resolvedRequests = resolveSaveRequests(validatedRequests);
        int itemCount = resolvedRequests.size();
        LOGGER.info(
                "Starting save batch: provider={}, bucket={}, itemCount={}, batchSize={}, maxConcurrentBatchItems={}, maxBatchItems={}, basePath={}, timeZone={}",
                defaultProvider,
                defaultBucket,
                itemCount,
                batchSize,
                maxConcurrentBatchItems,
                maxBatchItems,
                basePath,
                timeZone
        );
        List<StoredObject> savedObjects = processSaveFilesTransactionally(resolvedRequests);
        int savedItemCount = savedObjects.size();
        LOGGER.info(
                "Completed save batch: provider={}, bucket={}, itemCount={}",
                defaultProvider,
                defaultBucket,
                savedItemCount
        );
        return savedObjects;
    }

    @Override
    public List<RetrievedObject> getFiles(List<GetFileRequest> requests) {
        List<GetFileRequest> validatedRequests = validateBatchRequests(requests, "get");
        int itemCount = validatedRequests.size();
        LOGGER.info("Starting get batch: itemCount={}", itemCount);
        List<RetrievedObject> objects = processInBatches(validatedRequests, this::getOne, "get", false);
        int objectCount = objects.size();
        LOGGER.info("Completed get batch: itemCount={}", objectCount);
        return objects;
    }

    @Override
    public List<DeletedObject> deleteFiles(List<DeleteFileRequest> requests) {
        List<DeleteFileRequest> validatedRequests = validateBatchRequests(requests, "delete");
        int itemCount = validatedRequests.size();
        LOGGER.info("Starting delete batch: itemCount={}", itemCount);
        List<DeletedObject> deletedObjects = processInBatches(validatedRequests, this::deleteOne, "delete", false);
        int deletedItemCount = deletedObjects.size();
        LOGGER.info("Completed delete batch: itemCount={}", deletedItemCount);
        return deletedObjects;
    }

    @Override
    public List<StoredObject> copyFiles(List<CopyFileRequest> requests) {
        List<CopyFileRequest> validatedRequests = validateBatchRequests(requests, "copy");
        int itemCount = validatedRequests.size();
        LOGGER.info("Starting copy batch: itemCount={}", itemCount);
        List<StoredObject> copiedObjects = processInBatches(validatedRequests, this::copyOne, "copy", false);
        int copiedItemCount = copiedObjects.size();
        LOGGER.info("Completed copy batch: itemCount={}", copiedItemCount);
        return copiedObjects;
    }

    @Override
    public List<StoredObject> moveFiles(List<MoveFileRequest> requests) {
        List<MoveFileRequest> validatedRequests = validateBatchRequests(requests, "move");
        int itemCount = validatedRequests.size();
        LOGGER.info("Starting move batch: itemCount={}", itemCount);
        List<StoredObject> movedObjects = processInBatches(validatedRequests, this::moveOne, "move", false);
        int movedItemCount = movedObjects.size();
        LOGGER.info("Completed move batch: itemCount={}", movedItemCount);
        return movedObjects;
    }

    private StoredObject saveOne(ResolvedTarget target, UploadFileRequest request) {
        if (saveSemaphore == null) {
            return resolveClient(target.provider()).saveFile(target.bucket(), request);
        }

        acquireSavePermit();
        try {
            return resolveClient(target.provider()).saveFile(target.bucket(), request);
        } finally {
            saveSemaphore.release();
        }
    }

    private RetrievedObject getOne(GetFileRequest request) {
        ResolvedTarget target = resolveTarget(request.provider(), request.bucket());
        return resolveClient(target.provider()).getFile(target.bucket(), request);
    }

    private DeletedObject deleteOne(DeleteFileRequest request) {
        ResolvedTarget target = resolveTarget(request.provider(), request.bucket());
        resolveClient(target.provider()).deleteFile(target.bucket(), request);
        return new DeletedObject(target.provider(), target.bucket(), request.key());
    }

    private StoredObject copyOne(CopyFileRequest request) {
        ResolvedTarget sourceTarget = resolveTarget(request.sourceProvider(), request.sourceBucket());
        ResolvedTarget targetTarget = resolveTarget(request.targetProvider(), request.targetBucket());
        if (LOGGER.isDebugEnabled()) {
            StorageProvider sourceProvider = sourceTarget.provider();
            String sourceBucket = sourceTarget.bucket();
            String sourceKey = request.sourceKey();
            StorageProvider destinationProvider = targetTarget.provider();
            String destinationBucket = targetTarget.bucket();
            String destinationKey = request.targetKey();
            LOGGER.debug(
                    "Copy item started: sourceProvider={}, sourceBucket={}, sourceKey={}, targetProvider={}, targetBucket={}, targetKey={}",
                    sourceProvider,
                    sourceBucket,
                    sourceKey,
                    destinationProvider,
                    destinationBucket,
                    destinationKey
            );
        }
        RetrievedObject sourceObject = resolveClient(sourceTarget.provider()).getFile(
                sourceTarget.bucket(),
                new GetFileRequest(request.sourceKey(), sourceTarget.provider(), sourceTarget.bucket())
        );
        StoredObject storedObject = resolveClient(targetTarget.provider()).saveFile(
                targetTarget.bucket(),
                new UploadFileRequest(
                        request.targetKey(),
                        sourceObject.content(),
                        sourceObject.contentType(),
                        sourceObject.metadata(),
                        targetTarget.provider(),
                        targetTarget.bucket()
                )
        );
        StoredObject result = new StoredObject(
                storedObject.provider(),
                storedObject.bucket(),
                request.sourceKey(),
                storedObject.key(),
                extractFilename(request.sourceKey()),
                extractFilename(storedObject.key()),
                storedObject.eTag(),
                storedObject.versionId()
        );
        if (LOGGER.isDebugEnabled()) {
            StorageProvider sourceProvider = sourceTarget.provider();
            String sourceBucket = sourceTarget.bucket();
            String sourceKey = request.sourceKey();
            StorageProvider destinationProvider = targetTarget.provider();
            String destinationBucket = targetTarget.bucket();
            String destinationKey = result.key();
            LOGGER.debug(
                    "Copy item completed: sourceProvider={}, sourceBucket={}, sourceKey={}, targetProvider={}, targetBucket={}, targetKey={}",
                    sourceProvider,
                    sourceBucket,
                    sourceKey,
                    destinationProvider,
                    destinationBucket,
                    destinationKey
            );
        }
        return result;
    }

    private StoredObject moveOne(MoveFileRequest request) {
        ResolvedTarget sourceTarget = resolveTarget(request.sourceProvider(), request.sourceBucket());
        StoredObject copiedObject = copyOne(new CopyFileRequest(
                request.sourceKey(),
                request.targetKey(),
                request.sourceProvider(),
                request.sourceBucket(),
                request.targetProvider(),
                request.targetBucket()
        ));
        resolveClient(sourceTarget.provider()).deleteFile(
                sourceTarget.bucket(),
                new DeleteFileRequest(request.sourceKey(), sourceTarget.provider(), sourceTarget.bucket())
        );
        return copiedObject;
    }

    private List<StoredObject> processSaveFilesTransactionally(List<ResolvedSaveRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        LOGGER.info(
                "Save transactional processing started: provider={}, bucket={}, itemCount={}, chunkSize={}",
                defaultProvider,
                defaultBucket,
                requests.size(),
                batchSize
        );
        List<StoredObject> results = new ArrayList<>(Collections.nCopies(requests.size(), null));
        List<StoredObject> savedObjects = new ArrayList<>();
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int start = 0; start < requests.size(); start += batchSize) {
                int end = Math.min(start + batchSize, requests.size());
                LOGGER.info(
                        "Processing save chunk: provider={}, bucket={}, startIndex={}, endExclusive={}, chunkItemCount={}",
                        defaultProvider,
                        defaultBucket,
                        start,
                        end,
                        end - start
                );
                processSaveChunk(requests, results, savedObjects, start, end, executorService);
                LOGGER.info(
                        "Completed save chunk: provider={}, bucket={}, startIndex={}, endExclusive={}, savedSoFar={}",
                        defaultProvider,
                        defaultBucket,
                        start,
                        end,
                        savedObjects.size()
                );
            }
        } catch (ObjectStorageException ex) {
            rollbackSavedObjects(savedObjects, ex);
            throw ex;
        } catch (RuntimeException ex) {
            ObjectStorageException wrapped = new ObjectStorageException("Batch save failed", ex);
            rollbackSavedObjects(savedObjects, wrapped);
            throw wrapped;
        }
        LOGGER.info(
                "Save transactional processing finished: provider={}, bucket={}, itemCount={}, savedCount={}",
                defaultProvider,
                defaultBucket,
                requests.size(),
                savedObjects.size()
        );
        return List.copyOf(results);
    }

    private void processSaveChunk(
            List<ResolvedSaveRequest> requests,
            List<StoredObject> results,
            List<StoredObject> savedObjects,
            int startInclusive,
            int endExclusive,
            ExecutorService executorService
    ) {
        CompletionService<IndexedResult<StoredObject>> completionService = new ExecutorCompletionService<>(executorService);
        SaveChunkState state = submitInitialSaveItems(completionService, requests, startInclusive, endExclusive);
        LOGGER.debug(
                "Save chunk submitted initial items: startIndex={}, endExclusive={}, inFlight={}, nextIndex={}",
                startInclusive,
                endExclusive,
                state.inFlight(),
                state.nextIndex()
        );

        ObjectStorageException failure = null;
        while (state.inFlight() > 0) {
            try {
                IndexedResult<StoredObject> result = completionService.take().get();
                state = state.onItemCompleted();
                results.set(result.index(), result.value());
                savedObjects.add(result.value());
                String storedKey = result.value().key();
                LOGGER.debug(
                        "Save item completed: provider={}, bucket={}, index={}, storedKey={}, inFlightRemaining={}",
                        defaultProvider,
                        defaultBucket,
                        result.index(),
                        storedKey,
                        state.inFlight()
                );

                if (failure == null && state.hasRemainingItems(endExclusive)) {
                    int nextIndex = state.nextIndex();
                    ResolvedSaveRequest nextRequest = requests.get(nextIndex);
                    submitSaveItem(completionService, nextIndex, nextRequest);
                    String nextKey = nextRequest.storedRequest().key();
                    LOGGER.debug(
                            "Save item submitted after completion: index={}, key={}",
                            nextIndex,
                            nextKey
                    );
                    state = state.onItemSubmitted();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure = new ObjectStorageException("Batch save interrupted", ex);
                state = state.onItemCompleted();
                LOGGER.warn(
                        "Save chunk interrupted: provider={}, bucket={}, startIndex={}, endExclusive={}, inFlightAfterInterrupt={}, failure={}",
                        defaultProvider,
                        defaultBucket,
                        startInclusive,
                        endExclusive,
                        state.inFlight(),
                        summarizeFailure(ex)
                );
            } catch (ExecutionException ex) {
                state = state.onItemCompleted();
                Throwable cause = ex.getCause();
                if (failure == null) {
                    failure = toObjectStorageException("Batch save failed", cause);
                }
                LOGGER.warn(
                        "Save item execution failed: provider={}, bucket={}, startIndex={}, endExclusive={}, inFlightAfterFailure={}",
                        defaultProvider,
                        defaultBucket,
                        startInclusive,
                        endExclusive,
                        state.inFlight(),
                        cause
                );
            }
        }

        if (failure != null) {
            LOGGER.warn(
                    "Save chunk failed: provider={}, bucket={}, startIndex={}, endExclusive={}, savedBeforeChunkFailure={}",
                    defaultProvider,
                    defaultBucket,
                    startInclusive,
                    endExclusive,
                    savedObjects.size(),
                    failure
            );
            throw failure;
        }
    }

    private void submitSaveItem(
            CompletionService<IndexedResult<StoredObject>> completionService,
            int index,
            ResolvedSaveRequest request
    ) {
        completionService.submit(() -> {
            LOGGER.debug(
                    "Submitting save item to provider: provider={}, bucket={}, index={}, originalKey={}, storedKey={}",
                    request.target().provider(),
                    request.target().bucket(),
                    index,
                    request.originalRequest().key(),
                    request.storedRequest().key()
            );
            try {
                StoredObject storedObject = saveOne(request.target(), request.storedRequest());
                LOGGER.debug(
                        "Provider save success: provider={}, bucket={}, index={}, storedKey={}",
                        request.target().provider(),
                        request.target().bucket(),
                        index,
                        storedObject.key()
                );
                return new IndexedResult<>(index, enrichStoredObject(request, storedObject));
            } catch (RuntimeException ex) {
                throw createBatchFailure(operationFailureMessage(index, request.originalRequest()), ex);
            }
        });
    }

    private StoredObject enrichStoredObject(ResolvedSaveRequest request, StoredObject storedObject) {
        return new StoredObject(
                storedObject.provider(),
                storedObject.bucket(),
                request.originalRequest().key(),
                storedObject.key(),
                extractFilename(request.normalizedOriginalKey()),
                extractFilename(storedObject.key()),
                storedObject.eTag(),
                storedObject.versionId()
        );
    }

    private void rollbackSavedObjects(List<StoredObject> savedObjects, ObjectStorageException originalFailure) {
        if (savedObjects.isEmpty()) {
            LOGGER.info(
                    "No saved objects to roll back after save batch failure: provider={}, bucket={}",
                    defaultProvider,
                    defaultBucket,
                    originalFailure
            );
            return;
        }
        int savedObjectCount = savedObjects.size();
        LOGGER.warn(
                "Rolling back saved objects after save batch failure: provider={}, bucket={}, itemCount={}",
                defaultProvider,
                defaultBucket,
                savedObjectCount,
                originalFailure
        );
        for (int index = savedObjects.size() - 1; index >= 0; index--) {
            StoredObject savedObject = savedObjects.get(index);
            StorageProvider provider = savedObject.provider();
            String bucket = savedObject.bucket();
            String key = savedObject.key();
            try {
                LOGGER.info(
                        "Rolling back saved object: provider={}, bucket={}, key={}",
                        provider,
                        bucket,
                        key
                );
                resolveClient(provider).deleteFile(bucket, new DeleteFileRequest(key));
            } catch (RuntimeException rollbackFailure) {
                LOGGER.warn(
                        "Rollback delete failed: provider={}, bucket={}, key={}",
                        provider,
                        bucket,
                        key,
                        rollbackFailure
                );
                originalFailure.addSuppressed(new ObjectStorageException(
                        "Rollback delete failed for " + provider + ":" + bucket + "/" + key,
                        rollbackFailure
                ));
            }
        }
        LOGGER.warn(
                "Rollback processing completed: provider={}, bucket={}, attemptedItemCount={}",
                defaultProvider,
                defaultBucket,
                savedObjectCount
        );
    }

    @Override
    public List<StorageObjectInfo> listFiles(ListFilesRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ListFilesRequest resolvedRequest = resolveListRequest(request);
        ResolvedTarget target = resolveTarget(resolvedRequest.provider(), resolvedRequest.bucket());
        StorageProvider targetProvider = target.provider();
        String targetBucket = target.bucket();
        String prefix = resolvedRequest.prefix();
        Integer maxResults = resolvedRequest.maxResults();
        Integer requestedMaxResults = request.maxResults();
        LOGGER.info(
                "Listing files: provider={}, bucket={}, prefix={}, requestedMaxResults={}, effectiveMaxResults={}",
                targetProvider,
                targetBucket,
                prefix,
                requestedMaxResults,
                maxResults
        );
        List<StorageObjectInfo> objects = resolveClient(targetProvider).listFiles(targetBucket, resolvedRequest);
        int objectCount = objects.size();
        LOGGER.info(
                "Completed file listing: provider={}, bucket={}, itemCount={}",
                targetProvider,
                targetBucket,
                objectCount
        );
        return objects;
    }

    private ListFilesRequest resolveListRequest(ListFilesRequest request) {
        String prefix = request.prefix();
        Integer effectiveMaxResults = request.maxResults() != null ? request.maxResults() : defaultListMaxResults;
        return new ListFilesRequest(prefix, effectiveMaxResults, request.provider(), request.bucket());
    }

    @Override
    public void close() {
        for (ProviderClient provider : providers.values()) {
            try {
                provider.close();
            } catch (RuntimeException ex) {
                StorageProvider storageProvider = provider.provider();
                LOGGER.warn("Provider close failed: provider={}", storageProvider, ex);
            }
        }
    }

    private ProviderClient resolveClient(StorageProvider provider) {
        ProviderClient client = providers.get(provider);
        if (client == null) {
            throw new ObjectStorageException("Provider is not configured: " + provider);
        }
        return client;
    }

    private ResolvedTarget resolveTarget(StorageProvider providerOverride, String bucketOverride) {
        StorageProvider resolvedProvider = providerOverride == null ? defaultProvider : providerOverride;
        String resolvedBucket = ValidationUtils.normalizeToNull(bucketOverride);
        if (resolvedBucket == null) {
            resolvedBucket = defaultBucket;
        }
        if (!providers.containsKey(resolvedProvider)) {
            throw new ObjectStorageException("Provider is not configured: " + resolvedProvider);
        }
        return new ResolvedTarget(resolvedProvider, resolvedBucket);
    }

    private void acquireSavePermit() {
        try {
            saveSemaphore.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ObjectStorageException("Save operation interrupted while waiting for upload slot", ex);
        }
    }

    private <T, R> List<R> processInBatches(
            List<T> requests,
            Function<T, R> processor,
            String operation,
            boolean validateRequests
    ) {
        List<T> validatedRequests = validateRequests ? validateBatchRequests(requests, operation) : requests;
        if (validatedRequests.isEmpty()) {
            return List.of();
        }

        List<R> results = new ArrayList<>(Collections.nCopies(validatedRequests.size(), null));
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int start = 0; start < validatedRequests.size(); start += batchSize) {
                int end = Math.min(start + batchSize, validatedRequests.size());
                processChunk(validatedRequests, results, processor, operation, start, end, executorService);
            }
        }
        return List.copyOf(results);
    }

    private <T> List<T> validateBatchRequests(List<T> requests, String operation) {
        Objects.requireNonNull(requests, operation + " requests must not be null");
        if (requests.size() > maxBatchItems) {
            throw new IllegalArgumentException(
                    operation + " batch contains " + requests.size()
                            + " items, which exceeds configured maxBatchItems=" + maxBatchItems
            );
        }

        List<T> validatedRequests = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            T request = requests.get(index);
            if (request == null) {
                throw new IllegalArgumentException(operation + " request at index " + index + " must not be null");
            }
            validatedRequests.add(request);
        }
        return List.copyOf(validatedRequests);
    }

    private <T, R> void processChunk(
            List<T> requests,
            List<R> results,
            Function<T, R> processor,
            String operation,
            int startInclusive,
            int endExclusive,
            ExecutorService executorService
    ) {
        LOGGER.debug(
                "Starting {} chunk: provider={}, bucket={}, startIndex={}, endExclusive={}, chunkItemCount={}, maxConcurrentBatchItems={}",
                operation,
                defaultProvider,
                defaultBucket,
                startInclusive,
                endExclusive,
                endExclusive - startInclusive,
                maxConcurrentBatchItems
        );
        Semaphore batchSemaphore = new Semaphore(maxConcurrentBatchItems, true);
        List<Future<IndexedResult<R>>> futures = new ArrayList<>(endExclusive - startInclusive);

        for (int index = startInclusive; index < endExclusive; index++) {
            int requestIndex = index;
            T request = requests.get(index);
            futures.add(executorService.submit(
                    () -> processItem(requestIndex, request, processor, operation, batchSemaphore)
            ));
        }

        for (Future<IndexedResult<R>> future : futures) {
            try {
                IndexedResult<R> result = future.get();
                results.set(result.index(), result.value());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                cancelPending(futures);
                throw new ObjectStorageException(BATCH_PREFIX + operation + " interrupted", ex);
            } catch (ExecutionException ex) {
                cancelPending(futures);
                Throwable cause = ex.getCause();
                if (cause instanceof ObjectStorageException objectStorageException) {
                    throw objectStorageException;
                }
                throw new ObjectStorageException(BATCH_PREFIX + operation + " failed", cause);
            }
        }
        LOGGER.debug(
                "Completed {} chunk: provider={}, bucket={}, startIndex={}, endExclusive={}",
                operation,
                defaultProvider,
                defaultBucket,
                startInclusive,
                endExclusive
        );
    }

    private <T, R> IndexedResult<R> processItem(
            int index,
            T request,
            Function<T, R> processor,
            String operation,
            Semaphore batchSemaphore
    ) {
        acquireBatchPermit(operation, index, batchSemaphore);
        try {
            String requestSummary = describeRequest(request);
            LOGGER.debug(
                    "Processing {} item: provider={}, bucket={}, index={}, request={}",
                    operation,
                    defaultProvider,
                    defaultBucket,
                    index,
                    requestSummary
            );
            return new IndexedResult<>(index, processor.apply(request));
        } catch (RuntimeException ex) {
            throw createBatchFailure(operationFailureMessage(operation, index, request), ex);
        } finally {
            batchSemaphore.release();
        }
    }

    private void acquireBatchPermit(String operation, int index, Semaphore batchSemaphore) {
        try {
            batchSemaphore.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ObjectStorageException(
                    BATCH_PREFIX + operation + " interrupted while waiting for item slot at index " + index,
                    ex
            );
        }
    }

    private void cancelPending(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private String describeRequest(Object request) {
        if (request instanceof UploadFileRequest uploadFileRequest) {
            ResolvedTarget target = resolveTarget(uploadFileRequest.provider(), uploadFileRequest.bucket());
            return target.provider() + ":" + target.bucket() + "/" + uploadFileRequest.key();
        }
        if (request instanceof GetFileRequest getFileRequest) {
            ResolvedTarget target = resolveTarget(getFileRequest.provider(), getFileRequest.bucket());
            return target.provider() + ":" + target.bucket() + "/" + getFileRequest.key();
        }
        if (request instanceof DeleteFileRequest deleteFileRequest) {
            ResolvedTarget target = resolveTarget(deleteFileRequest.provider(), deleteFileRequest.bucket());
            return target.provider() + ":" + target.bucket() + "/" + deleteFileRequest.key();
        }
        if (request instanceof CopyFileRequest copyFileRequest) {
            ResolvedTarget sourceTarget = resolveTarget(copyFileRequest.sourceProvider(), copyFileRequest.sourceBucket());
            ResolvedTarget targetTarget = resolveTarget(copyFileRequest.targetProvider(), copyFileRequest.targetBucket());
            return sourceTarget.provider() + ":" + sourceTarget.bucket() + "/" + copyFileRequest.sourceKey()
                    + " -> " + targetTarget.provider() + ":" + targetTarget.bucket() + "/" + copyFileRequest.targetKey();
        }
        if (request instanceof MoveFileRequest moveFileRequest) {
            ResolvedTarget sourceTarget = resolveTarget(moveFileRequest.sourceProvider(), moveFileRequest.sourceBucket());
            ResolvedTarget targetTarget = resolveTarget(moveFileRequest.targetProvider(), moveFileRequest.targetBucket());
            return sourceTarget.provider() + ":" + sourceTarget.bucket() + "/" + moveFileRequest.sourceKey()
                    + " -> " + targetTarget.provider() + ":" + targetTarget.bucket() + "/" + moveFileRequest.targetKey();
        }
        return String.valueOf(request);
    }

    private List<ResolvedSaveRequest> resolveSaveRequests(List<UploadFileRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        String savePrefix = buildSavePrefix();
        Set<String> usedKeys = new HashSet<>();
        List<ResolvedSaveRequest> resolvedRequests = new ArrayList<>(requests.size());

        for (int index = 0; index < requests.size(); index++) {
            UploadFileRequest originalRequest = requests.get(index);
            ResolvedTarget target = resolveTarget(originalRequest.provider(), originalRequest.bucket());
            validateSaveRequest(originalRequest);
            String normalizedOriginalKey = normalizeUserKey(originalRequest.key());
            String candidateKey = appendPath(savePrefix, normalizedOriginalKey);
            String storedKey = ensureUniqueKey(candidateKey, usedKeys);

            UploadFileRequest storedRequest = new UploadFileRequest(
                    storedKey,
                    originalRequest.content(),
                    originalRequest.contentType(),
                    originalRequest.metadata(),
                    target.provider(),
                    target.bucket()
            );
            resolvedRequests.add(new ResolvedSaveRequest(originalRequest, storedRequest, normalizedOriginalKey, target));
            String originalKey = originalRequest.key();
            if (LOGGER.isDebugEnabled()) {
                StorageProvider resolvedProvider = target.provider();
                String resolvedBucket = target.bucket();
                LOGGER.debug(
                        "Resolved save key: provider={}, bucket={}, originalKey={}, storedKey={}",
                        resolvedProvider,
                        resolvedBucket,
                        originalKey,
                        storedKey
                );
            }
        }

        return List.copyOf(resolvedRequests);
    }

    private void validateSaveRequest(UploadFileRequest request) {
        String normalizedOriginalKey = normalizeUserKey(request.key());
        if (allowedFileExtensions != null) {
            String extension = extractExtension(normalizedOriginalKey);
            if (!allowedFileExtensions.contains(extension)) {
                throw new IllegalArgumentException(
                        "file extension '" + extension + "' is not allowed; allowedFileExtensions=" + allowedFileExtensions
                );
            }
        }
        if (maxFileSizeBytes != null && request.content().length > maxFileSizeBytes) {
            throw new IllegalArgumentException(
                    "file size " + request.content().length + " bytes exceeds maxFileSizeBytes=" + maxFileSizeBytes
            );
        }
    }

    private String extractExtension(String normalizedKey) {
        String filename = extractFilename(normalizedKey);
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("file extension is required for key: " + normalizedKey);
        }
        return filename.substring(dotIndex + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private String buildSavePrefix() {
        String timestampPath = SAVE_PATH_FORMATTER.format(ZonedDateTime.now(clock.withZone(timeZone)));
        return appendPath(basePath, timestampPath);
    }

    private String ensureUniqueKey(String candidateKey, Set<String> usedKeys) {
        if (usedKeys.add(candidateKey)) {
            return candidateKey;
        }

        String parentPath = parentPath(candidateKey);
        String filename = extractFilename(candidateKey);
        int counter = 1;
        String renamedKey;
        do {
            renamedKey = appendPath(parentPath, counter + "_" + filename);
            counter++;
        } while (!usedKeys.add(renamedKey));
        return renamedKey;
    }

    private String normalizeUserKey(String key) {
        String normalizedKey = normalizePath(key);
        if (normalizedKey == null) {
            throw new IllegalArgumentException("key must not be blank");
        }
        extractFilename(normalizedKey);
        return normalizedKey;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String[] rawSegments = path.trim().replace('\\', '/').split("/");
        List<String> normalizedSegments = new ArrayList<>(rawSegments.length);
        for (String rawSegment : rawSegments) {
            if (!rawSegment.isBlank()) {
                normalizedSegments.add(rawSegment.trim());
            }
        }

        if (normalizedSegments.isEmpty()) {
            return null;
        }
        return String.join("/", normalizedSegments);
    }

    private String appendPath(String left, String right) {
        String normalizedLeft = normalizePath(left);
        String normalizedRight = normalizePath(right);
        if (normalizedLeft == null) {
            return normalizedRight;
        }
        if (normalizedRight == null) {
            return normalizedLeft;
        }
        return normalizedLeft + "/" + normalizedRight;
    }

    private String parentPath(String key) {
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(0, lastSlash) : null;
    }

    private String extractFilename(String key) {
        int lastSlash = key.lastIndexOf('/');
        String filename = lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
        if (filename.isBlank()) {
            throw new IllegalArgumentException("key must reference a file name");
        }
        return filename;
    }

    private SaveChunkState submitInitialSaveItems(
            CompletionService<IndexedResult<StoredObject>> completionService,
            List<ResolvedSaveRequest> requests,
            int startInclusive,
            int endExclusive
    ) {
        SaveChunkState state = new SaveChunkState(startInclusive, 0);
        while (state.hasRemainingItems(endExclusive) && state.inFlight() < maxConcurrentBatchItems) {
            submitSaveItem(completionService, state.nextIndex(), requests.get(state.nextIndex()));
            state = state.onItemSubmitted();
        }
        return state;
    }

    private ObjectStorageException createBatchFailure(String message, RuntimeException cause) {
        return cause instanceof ObjectStorageException objectStorageException
                ? new ObjectStorageException(message, objectStorageException)
                : new ObjectStorageException(message, cause);
    }

    private ObjectStorageException toObjectStorageException(String message, Throwable cause) {
        return cause instanceof ObjectStorageException objectStorageException
                ? objectStorageException
                : new ObjectStorageException(message, cause);
    }

    private String operationFailureMessage(int index, UploadFileRequest request) {
        return BATCH_PREFIX + "save failed at index " + index + " (" + describeRequest(request) + ")";
    }

    private String operationFailureMessage(String operation, int index, Object request) {
        return BATCH_PREFIX + operation + " failed at index " + index + " (" + describeRequest(request) + ")";
    }

    private String summarizeFailure(Throwable failure) {
        Throwable rootCause = rootCause(failure);
        String rootType = rootCause.getClass().getSimpleName();
        String rootMessage = rootCause.getMessage();
        if (rootMessage == null || rootMessage.isBlank()) {
            return rootType;
        }
        return rootType + ": " + rootMessage;
    }

    private Throwable rootCause(Throwable failure) {
        Throwable root = Objects.requireNonNull(failure, "failure must not be null");
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private record IndexedResult<R>(int index, R value) {
    }

    public record ServiceSettings(
            int batchSize,
            int maxConcurrentBatchItems,
            int maxBatchItems,
            Integer defaultListMaxResults,
            Set<String> allowedFileExtensions,
            Long maxFileSizeBytes,
            String basePath,
            ZoneId timeZone,
            Clock clock
    ) {
        static ServiceSettings defaultSettings() {
            ZoneId zoneId = ObjectStorageServiceBuilder.DEFAULT_TIME_ZONE;
            return new ServiceSettings(
                    ObjectStorageServiceBuilder.DEFAULT_BATCH_SIZE,
                    ObjectStorageServiceBuilder.DEFAULT_MAX_CONCURRENT_BATCH_ITEMS,
                    ObjectStorageServiceBuilder.DEFAULT_MAX_BATCH_ITEMS,
                    ObjectStorageServiceBuilder.DEFAULT_LIST_MAX_RESULTS,
                    null,
                    null,
                    null,
                    zoneId,
                    Clock.system(zoneId)
            );
        }
    }

    private record SaveChunkState(int nextIndex, int inFlight) {
        private SaveChunkState onItemSubmitted() {
            return new SaveChunkState(nextIndex + 1, inFlight + 1);
        }

        private SaveChunkState onItemCompleted() {
            return new SaveChunkState(nextIndex, inFlight - 1);
        }

        private boolean hasRemainingItems(int endExclusive) {
            return nextIndex < endExclusive;
        }
    }

    private record ResolvedSaveRequest(
            UploadFileRequest originalRequest,
            UploadFileRequest storedRequest,
            String normalizedOriginalKey,
            ResolvedTarget target
    ) {
    }

    private record ResolvedTarget(StorageProvider provider, String bucket) {
    }
}
