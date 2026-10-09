package com.thuvstu.hayatemod.save;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveFilesTest {
    @TempDir
    Path temp;

    @Test
    void missingFileCanBeCreatedAndRoundTripped() {
        Path file = temp.resolve("new.json");
        assertNull(SaveFiles.load(file, Map.class, "test"));
        SaveFiles.save(file, Map.of("level", 12), "test");
        Map<?, ?> loaded = SaveFiles.load(file, Map.class, "test");
        assertNotNull(loaded);
        assertEquals(12.0, loaded.get("level"));
    }

    @Test
    void legacyAndV1PayloadsMigrateToVersionedEnvelope() throws IOException {
        for (String original : new String[] {"{\"level\":5}",
                "{\"save_version\":1,\"data\":{\"level\":5}}"}) {
            Path file = temp.resolve("legacy.json");
            Files.writeString(file, original);
            Map<?, ?> data = SaveFiles.load(file, Map.class, "test");
            assertNotNull(data);
            assertEquals(5.0, data.get("level"));
            SaveFiles.save(file, data, "test");
            var migrated = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertEquals(2, migrated.get("save_version").getAsInt());
            assertEquals(1, migrated.get("content_version").getAsInt());
            assertEquals(5, migrated.getAsJsonObject("data").get("level").getAsInt());
        }
    }

    @Test
    void futureSaveVersionIsNeverOverwrittenByEmptyFallback() throws IOException {
        protectedFile("{\"save_version\":99,\"content_version\":1,\"data\":{\"level\":99}}");
    }

    @Test
    void futureContentVersionIsNeverOverwritten() throws IOException {
        protectedFile("{\"save_version\":2,\"content_version\":99,\"data\":{}}");
    }

    @Test
    void corruptAndNullPayloadsAreProtected() throws IOException {
        protectedFile("{not valid json");
        protectedFile("{\"save_version\":2,\"content_version\":1,\"data\":null}");
        protectedFile("{\"save_version\":2,\"content_version\":1,\"data\":\"wrong shape\"}");
    }

    @Test
    void malformedVersionFieldsAreNotCoerced() throws IOException {
        protectedFile("{\"save_version\":\"2\",\"content_version\":1,\"data\":{}}");
        protectedFile("{\"save_version\":2.5,\"content_version\":1,\"data\":{}}");
        protectedFile("{\"save_version\":2,\"content_version\":null,\"data\":{}}");
        protectedFile("{\"save_version\":2,\"data\":{}}");
        protectedFile("{\"content_version\":1,\"data\":{}}");
    }

    @Test
    void saveWithoutPriorLoadStillProtectsFutureFile() throws IOException {
        Path file = temp.resolve("future.json");
        String original = "{\"save_version\":99,\"data\":{\"level\":99}}";
        Files.writeString(file, original);
        SaveFiles.save(file, Map.of(), "test");
        assertEquals(original, Files.readString(file));
    }

    @Test
    void failedSerializationDoesNotTouchPreviousSave() throws IOException {
        Path file = temp.resolve("valid.json");
        SaveFiles.save(file, Map.of("level", 7), "test");
        String original = Files.readString(file);
        SaveFiles.save(file, Map.of("invalid", Double.NaN), "test");
        assertEquals(original, Files.readString(file));
        SaveFiles.save(file, null, "test");
        assertEquals(original, Files.readString(file));
    }

    private void protectedFile(String original) throws IOException {
        Path file = Files.createTempFile(temp, "protected-", ".json");
        Files.writeString(file, original);
        assertNull(SaveFiles.load(file, Map.class, "test"));
        SaveFiles.save(file, Map.of(), "test");
        assertEquals(original, Files.readString(file));
    }
}
