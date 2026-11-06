package dev.roocky.emitreetabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The guarantee that matters: a failed save never costs the player the save they already had.
 *
 * <p>Failures are injected through the content writer, which is where a real one happens: the
 * serialiser throws, or the disk fills up, after the file has been opened.
 */
class SafeFilesTest {

	@TempDir
	Path dir;

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("a first write creates the file and its parent folder, with no backup yet")
	void firstWrite() throws IOException {
		Path target = dir.resolve("config/tabs.json");
		SafeFiles.write(target, writer -> writer.write("one"));
		assertEquals("one", read(target));
		assertFalse(Files.exists(SafeFiles.backupOf(target)));
	}

	@Test
	@DisplayName("a successful write replaces the file and keeps the previous one as the backup")
	void replaceKeepsBackup() throws IOException {
		Path target = dir.resolve("tabs.json");
		SafeFiles.write(target, writer -> writer.write("one"));
		SafeFiles.write(target, writer -> writer.write("two"));
		assertEquals("two", read(target));
		assertEquals("one", read(SafeFiles.backupOf(target)));
	}

	@Test
	@DisplayName("a writer that fails half way leaves the old file intact and no temporary file")
	void failureMidWriteKeepsOldFile() throws IOException {
		Path target = dir.resolve("tabs.json");
		SafeFiles.write(target, writer -> writer.write("good"));

		assertThrows(IOException.class, () -> SafeFiles.write(target, writer -> {
			writer.write("partial");
			throw new IOException("disk full");
		}));

		assertEquals("good", read(target));
		assertFalse(Files.exists(target.resolveSibling("tabs.json.tmp")));
		// The backup is only refreshed by a write that is about to succeed.
		assertFalse(Files.exists(SafeFiles.backupOf(target)));
	}

	@Test
	@DisplayName("an unchecked serialiser failure is reported as an IOException, old file intact")
	void uncheckedFailureIsWrapped() throws IOException {
		Path target = dir.resolve("tabs.json");
		SafeFiles.write(target, writer -> writer.write("good"));

		assertThrows(IOException.class, () -> SafeFiles.write(target, writer -> {
			throw new IllegalStateException("serialiser broke");
		}));
		assertEquals("good", read(target));
	}

	@Test
	@DisplayName("after a failure, retrying with good content succeeds")
	void retryAfterFailure() throws IOException {
		Path target = dir.resolve("tabs.json");
		SafeFiles.write(target, writer -> writer.write("v1"));
		assertThrows(IOException.class, () -> SafeFiles.write(target, writer -> {
			throw new IOException("transient");
		}));
		SafeFiles.write(target, writer -> writer.write("v2"));
		assertEquals("v2", read(target));
		assertEquals("v1", read(SafeFiles.backupOf(target)));
	}

	@Test
	@DisplayName("a stale temporary file from a crashed run does not stop the next write")
	void staleTemporaryIsOverwritten() throws IOException {
		Path target = dir.resolve("tabs.json");
		Files.writeString(target.resolveSibling("tabs.json.tmp"), "left over from a crash");
		SafeFiles.write(target, writer -> writer.write("fresh"));
		assertEquals("fresh", read(target));
		assertFalse(Files.exists(target.resolveSibling("tabs.json.tmp")));
	}

	@Test
	@DisplayName("an unreadable file is moved aside, not deleted, so the next save cannot destroy it")
	void corruptFileIsSetAside() throws IOException {
		Path target = dir.resolve("tabs.json");
		Files.writeString(target, "{ not json");

		Path aside = SafeFiles.setAsideCorrupt(target);

		assertEquals(SafeFiles.corruptCopyOf(target), aside);
		assertFalse(Files.exists(target));
		assertTrue(Files.exists(aside));
		assertEquals("{ not json", read(aside));
	}
}
