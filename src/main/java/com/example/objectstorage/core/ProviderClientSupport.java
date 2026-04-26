package com.example.objectstorage.core;

import com.example.objectstorage.api.response.StorageObjectInfo;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;

public final class ProviderClientSupport {
    public static final String REQUEST_MUST_NOT_BE_NULL = "request must not be null";

    private final String providerName;
    private final String bucketLabel;

    public ProviderClientSupport(String providerName, String bucketLabel) {
        this.providerName = Objects.requireNonNull(providerName, "providerName must not be null");
        this.bucketLabel = Objects.requireNonNull(bucketLabel, "bucketLabel must not be null");
    }

    public <T> T executeKeyOperation(
            Logger logger,
            String operation,
            String bucket,
            String key,
            Supplier<T> supplier
    ) {
        logger.debug("{} {} started: {}={}, key={}", providerName, operation, bucketLabel, bucket, key);
        try {
            return supplier.get();
        } catch (ObjectStorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ObjectStorageException(
                    providerName + " " + operation + " failed for " + bucketLabel + "/key: " + bucket + "/" + key,
                    ex
            );
        }
    }

    public void executeVoidKeyOperation(
            Logger logger,
            String operation,
            String bucket,
            String key,
            Runnable runnable
    ) {
        executeKeyOperation(logger, operation, bucket, key, () -> {
            runnable.run();
            return null;
        });
    }

    public List<StorageObjectInfo> executeListOperation(
            Logger logger,
            String bucket,
            String prefix,
            Integer maxResults,
            Supplier<List<StorageObjectInfo>> supplier
    ) {
        logger.debug(
                "{} list started: {}={}, prefix={}, maxResults={}",
                providerName,
                bucketLabel,
                bucket,
                prefix,
                maxResults
        );
        try {
            return supplier.get();
        } catch (ObjectStorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ObjectStorageException(providerName + " list failed for " + bucketLabel + ": " + bucket, ex);
        }
    }

}
