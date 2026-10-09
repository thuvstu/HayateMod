package com.thuvstu.hayatemod.core.content;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.thuvstu.hayatemod.core.TestContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentRepositoryTest {
    @TempDir
    Path temp;

    @Test
    void repositoryPackPassesAllGates() {
        var result = ContentPipeline.load(TestContent.dir());
        assertTrue(result.ok(), () -> result.loaderErrors() + " " + result.issues());
    }

    @Test
    void parseFailureRetainsDatasetAndSource() throws IOException {
        var repo = initialized();
        var previous = repo.current();
        Path candidate = copyPack(temp.resolve("invalid"));
        Files.writeString(candidate.resolve("weapons/broken.yaml"), "id: [unterminated");
        assertFalse(repo.reload(candidate).ok());
        assertSame(previous, repo.current());
    }

    @Test
    void validationFailureRetainsDatasetAndSource() throws IOException {
        var repo = initialized();
        var previous = repo.current();
        Path candidate = copyPack(temp.resolve("invalid"));
        // Parsing succeeds, but an enemy still references this missing table.
        Files.delete(candidate.resolve("loot/flame_golem_loot.yaml"));
        var result = repo.reload(candidate);
        assertTrue(result.loaderErrors().isEmpty(), result.loaderErrors().toString());
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("V04")));
        assertSame(previous, repo.current());
    }

    @Test
    void descriptionFailureRetainsDatasetAndSource() throws IOException {
        var repo = initialized();
        var previous = repo.current();
        Path candidate = copyPack(temp.resolve("invalid"));
        Path weapon = candidate.resolve("weapons/ember_branch.yaml");
        // Unknown resource names are a description-template error even if parsed.
        Files.writeString(weapon, Files.readString(weapon).replace("resource: stamina",
                "resource: unsupported_resource"));
        var result = repo.reload(candidate);
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("V10")),
                result.issues().toString());
        assertSame(previous, repo.current());
    }

    @Test
    void successfulReloadReplacesSnapshot() throws IOException {
        var repo = initialized();
        var previous = repo.current();
        Path candidate = copyPack(temp.resolve("valid"));
        assertTrue(repo.reload(candidate).ok());
        assertNotSame(previous, repo.current());
        assertEquals(candidate.toAbsolutePath().normalize(), repo.current().source());
    }

    @Test
    void initialFailureDoesNotPublishPartialData() {
        var repo = new ContentRepository();
        assertFalse(repo.reload(temp.resolve("missing")).ok());
        assertNull(repo.current());
    }

    @Test
    void packCanLoadDirectlyFromJarFileSystem() throws IOException {
        URI uri = URI.create("jar:" + temp.resolve("pack.jar").toUri());
        try (var fs = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path content = copyPack(fs.getPath("/content"));
            var result = ContentPipeline.load(content);
            assertTrue(result.ok(), () -> result.loaderErrors() + " " + result.issues());
        }
    }

    private ContentRepository initialized() {
        var repo = new ContentRepository();
        var result = repo.reload(TestContent.dir());
        assertTrue(result.ok(), () -> result.loaderErrors() + " " + result.issues());
        return repo;
    }

    private Path copyPack(Path target) throws IOException {
        Path source = TestContent.dir();
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                // Resolve a string so this also works across default and JAR providers.
                Path destination = target.resolve(source.relativize(path).toString().replace('\\', '/'));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
        return target;
    }
}
