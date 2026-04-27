package com.example.objectstorage.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.objectstorage.api.response.StorageObjectInfo;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ProviderClientSupportTest {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ProviderClientSupportTest.class);

    @Test
    void shouldReturnKeyOperationResult() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");

        String result = support.executeKeyOperation(LOGGER, "upload", "docs", "a.txt", () -> "saved");

        assertEquals("saved", result);
    }

    @Test
    void shouldRethrowObjectStorageExceptionForKeyOperation() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");
        ObjectStorageException expected = new ObjectStorageException("boom");

        ObjectStorageException thrown = assertThrows(
                ObjectStorageException.class,
                () -> support.executeKeyOperation(LOGGER, "upload", "docs", "a.txt", () -> {
                    throw expected;
                })
        );

        assertSame(expected, thrown);
    }

    @Test
    void shouldWrapGenericExceptionForKeyOperation() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");

        ObjectStorageException thrown = assertThrows(
                ObjectStorageException.class,
                () -> support.executeKeyOperation(LOGGER, "download", "docs", "a.txt", () -> {
                    throw new IllegalStateException("boom");
                })
        );

        assertEquals("S3 download failed for bucket/key: docs/a.txt", thrown.getMessage());
    }

    @Test
    void shouldExecuteVoidKeyOperation() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");
        int[] invocationCount = {0};

        support.executeVoidKeyOperation(LOGGER, "delete", "docs", "a.txt", () -> invocationCount[0]++);

        assertEquals(1, invocationCount[0]);
    }

    @Test
    void shouldReturnListOperationResult() {
        ProviderClientSupport support = new ProviderClientSupport("Azure", "container");
        List<StorageObjectInfo> expected = List.of(new StorageObjectInfo("a.txt", 5L, Instant.parse("2026-01-01T00:00:00Z")));

        List<StorageObjectInfo> result = support.executeListOperation(
                LOGGER,
                "docs",
                "prefix/",
                10,
                () -> expected
        );

        assertSame(expected, result);
    }

    @Test
    void shouldWrapGenericExceptionForListOperation() {
        ProviderClientSupport support = new ProviderClientSupport("Azure", "container");

        ObjectStorageException thrown = assertThrows(
                ObjectStorageException.class,
                () -> support.executeListOperation(LOGGER, "docs", null, null, () -> {
                    throw new IllegalArgumentException("boom");
                })
        );

        assertEquals("Azure list failed for container: docs", thrown.getMessage());
    }

    @Test
    void shouldRethrowObjectStorageExceptionForListOperation() {
        ProviderClientSupport support = new ProviderClientSupport("Azure", "container");
        ObjectStorageException expected = new ObjectStorageException("boom");

        ObjectStorageException thrown = assertThrows(
                ObjectStorageException.class,
                () -> support.executeListOperation(LOGGER, "docs", null, null, () -> {
                    throw expected;
                })
        );

        assertSame(expected, thrown);
    }

    @Test
    void shouldPassVersionPreconditionWhenExpectedMatchesCurrent() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");

        support.validateExpectedVersion(
                LOGGER,
                "docs",
                "a.txt",
                "v1",
                () -> "v1"
        );
    }

    @Test
    void shouldSkipVersionValidationWhenExpectedVersionIsNull() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");

        support.validateExpectedVersion(
                LOGGER,
                "docs",
                "a.txt",
                null,
                () -> {
                    throw new AssertionError("supplier should not be called when expectedVersionId is null");
                }
        );
    }

    @Test
    void shouldFailVersionPreconditionWhenExpectedDoesNotMatchCurrent() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");

        ObjectStorageException thrown = assertThrows(
                ObjectStorageException.class,
                () -> support.validateExpectedVersion(LOGGER, "docs", "a.txt", "v1", () -> "v2")
        );

        assertEquals(
                "S3 save version already exists for bucket/key docs/a.txt: expectedVersionId=v1, currentVersionId=v2. "
                        + "Set versionOverride=true to allow saving a new version.",
                thrown.getMessage()
        );
    }

    @Test
    void shouldPassVersionPreconditionWhenOverrideEnabledAndVersionDoesNotMatch() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");

        support.validateExpectedVersion(
                LOGGER,
                "docs",
                "a.txt",
                "v1",
                true,
                () -> "v2"
        );
    }

    @Test
    void shouldPassVersionPreconditionWhenOverrideEnabledAndCurrentVersionMissing() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");

        support.validateExpectedVersion(
                LOGGER,
                "docs",
                "a.txt",
                "v1",
                true,
                () -> null
        );
    }

    @Test
    void shouldWrapGenericExceptionFromVersionSupplier() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");

        ObjectStorageException thrown = assertThrows(
                ObjectStorageException.class,
                () -> support.validateExpectedVersion(LOGGER, "docs", "a.txt", "v1", () -> {
                    throw new IllegalStateException("boom");
                })
        );

        assertEquals("S3 version precondition check failed for bucket/key: docs/a.txt", thrown.getMessage());
    }

    @Test
    void shouldRethrowObjectStorageExceptionFromVersionSupplier() {
        ProviderClientSupport support = new ProviderClientSupport("S3", "bucket");
        ObjectStorageException expected = new ObjectStorageException("boom");

        ObjectStorageException thrown = assertThrows(
                ObjectStorageException.class,
                () -> support.validateExpectedVersion(LOGGER, "docs", "a.txt", "v1", () -> {
                    throw expected;
                })
        );

        assertSame(expected, thrown);
    }
}
