package com.thuvstu.hayatemod.core.save;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Never truncate the previous save: write, flush, then atomically replace on the same filesystem. */
public final class AtomicSaveFile {
    private AtomicSaveFile() {
    }

    @FunctionalInterface
    interface Replacer {
        void replace(Path temporary, Path destination) throws IOException;
    }

    public static void write(Path destination, String text) throws IOException {
        write(destination, text, (temporary, target) -> Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    static void write(Path destination, String text, Replacer replacer) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".solommo-save-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(text);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            // Fail closed if atomic replacement is unsupported; no unsafe truncate/copy fallback.
            replacer.replace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
