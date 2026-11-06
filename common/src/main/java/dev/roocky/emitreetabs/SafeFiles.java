package dev.roocky.emitreetabs;

import java.io.IOException;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes the mod's json files without ever leaving a half-written one behind.
 *
 * <p>The tabs file is the player's work. Writing it in place meant the destination was truncated
 * the moment the writer opened, so a crash, a full disk or a serialiser exception part way through
 * left an empty or cut-off file, and the next launch quietly started with no tabs. Every write now
 * goes to a sibling temporary file first and only replaces the real one once it is complete.
 *
 * <p>Two more files can appear next to the real one:
 * <ul>
 * <li>{@code <name>.bak}, the previous good version, refreshed on every successful write. If the
 * real file cannot be parsed, readers fall back to it.</li>
 * <li>{@code <name>.corrupt}, an unreadable file moved out of the way rather than overwritten, so
 * whatever was in it can still be recovered by hand.</li>
 * </ul>
 *
 * <p>! Atomic replacement protects against a write being interrupted. It does not promise the data
 * survives a power cut: that also depends on the filesystem and the drive. The temporary file is
 * flushed to disk before the move to make that case as unlikely as this code can.
 *
 * <p>Imports nothing from Minecraft or EMI, so it is covered by plain JUnit tests.
 */
public final class SafeFiles {

	/** Produces the file's contents. Allowed to throw; the destination is left untouched if it does. */
	@FunctionalInterface
	public interface ContentWriter {
		void writeTo(Writer writer) throws IOException;
	}

	private SafeFiles() {
	}

	public static Path backupOf(Path target) {
		return target.resolveSibling(target.getFileName() + ".bak");
	}

	public static Path corruptCopyOf(Path target) {
		return target.resolveSibling(target.getFileName() + ".corrupt");
	}

	private static Path temporaryOf(Path target) {
		return target.resolveSibling(target.getFileName() + ".tmp");
	}

	/**
	 * Replaces {@code target} with what {@code content} writes, or leaves it exactly as it was.
	 *
	 * @throws IOException when anything failed; the previous file is still in place and still valid.
	 */
	public static void write(Path target, ContentWriter content) throws IOException {
		Path parent = target.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = temporaryOf(target);
		try {
			writeAndSync(temporary, content);
			keepBackup(target);
			replace(temporary, target);
		} catch (IOException | RuntimeException e) {
			// Gson reports a failing writer as an unchecked JsonIOException; either way the half
			// written temporary file must not be left lying around looking like a real save.
			Files.deleteIfExists(temporary);
			throw e instanceof IOException io ? io : new IOException("could not write " + target, e);
		}
	}

	/**
	 * Moves a file that could not be parsed to {@link #corruptCopyOf}, so the next save does not
	 * overwrite it and nothing in it is lost. Replaces an older corrupt copy if there is one.
	 *
	 * @return where the file now is, or null if it could not be moved.
	 */
	public static Path setAsideCorrupt(Path target) {
		Path aside = corruptCopyOf(target);
		try {
			Files.move(target, aside, StandardCopyOption.REPLACE_EXISTING);
			return aside;
		} catch (IOException e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not move unreadable {} aside", target, e);
			return null;
		}
	}

	private static void writeAndSync(Path temporary, ContentWriter content) throws IOException {
		try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			content.writeTo(writer);
		}
		try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
			channel.force(true);
		}
	}

	/**
	 * Copies the current file to {@link #backupOf} before it is replaced.
	 *
	 * <p>A failed backup is logged and does not stop the save: refusing to save the player's newest
	 * state because the copy of their older state failed would be the worse trade.
	 */
	private static void keepBackup(Path target) {
		if (!Files.exists(target)) {
			return;
		}
		try {
			Files.copy(target, backupOf(target), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not refresh the backup of {}", target, e);
		}
	}

	private static void replace(Path temporary, Path target) throws IOException {
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			// Some filesystems (certain network and FAT mounts) cannot rename atomically. A plain
			// replacing move still never exposes a partly written file, because the temporary file
			// is already complete; it only loses the guarantee that no reader sees "no file at all".
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
