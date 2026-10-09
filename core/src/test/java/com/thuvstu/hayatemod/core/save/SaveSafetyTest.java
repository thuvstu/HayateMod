package com.thuvstu.hayatemod.core.save;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveSafetyTest {
    @TempDir
    Path temp;

    @Test
    void explicitlyMigratesBareAndV1Envelopes() {
        assertEquals(1, SaveVersions.migratedContentVersion(0, null));
        assertEquals(1, SaveVersions.migratedContentVersion(1, null));
        assertEquals(1, SaveVersions.migratedContentVersion(2, 1));
    }

    @Test
    void rejectsFutureNegativeAndIncompleteVersions() {
        assertThrows(IllegalArgumentException.class, () -> SaveVersions.migratedContentVersion(3, 1));
        assertThrows(IllegalArgumentException.class, () -> SaveVersions.migratedContentVersion(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> SaveVersions.migratedContentVersion(2, null));
        assertThrows(IllegalArgumentException.class, () -> SaveVersions.migratedContentVersion(2, 2));
        assertThrows(IllegalArgumentException.class, () -> SaveVersions.migratedContentVersion(2, 0));
    }

    @Test
    void createsDirectoriesAndReplacesUtf8Save() throws IOException {
        Path file = temp.resolve("nested/profile.json");
        AtomicSaveFile.write(file, "旧セーブ");
        AtomicSaveFile.write(file, "新しい進行");
        assertEquals("新しい進行", Files.readString(file));
        assertNoTemporaryFiles(file.getParent());
    }

    @Test
    void failedReplacementRetainsOldBytesAndCleansTemporaryFile() throws IOException {
        Path file = temp.resolve("profile.json");
        Files.writeString(file, "old");
        assertThrows(IOException.class, () -> AtomicSaveFile.write(file, "new", (source, target) -> {
            assertEquals("new", Files.readString(source));
            assertEquals("old", Files.readString(target));
            assertEquals(source.getParent(), target.getParent());
            throw new IOException("injected disk failure");
        }));
        assertEquals("old", Files.readString(file));
        assertNoTemporaryFiles(temp);
    }

    @Test
    void unsupportedAtomicMoveFailsClosed() throws IOException {
        Path file = temp.resolve("profile.json");
        Files.writeString(file, "old");
        assertThrows(AtomicMoveNotSupportedException.class,
                () -> AtomicSaveFile.write(file, "new", (source, target) -> {
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test");
                }));
        assertEquals("old", Files.readString(file));
        assertNoTemporaryFiles(temp);
    }

    private void assertNoTemporaryFiles(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            assertTrue(paths.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }
}
