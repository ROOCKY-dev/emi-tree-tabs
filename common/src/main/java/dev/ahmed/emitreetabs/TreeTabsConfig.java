package dev.ahmed.emitreetabs;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.ahmed.emitreetabs.platform.Platform;


/**
 * A small hand rolled json config. Forge's config system is server aware and versioned, which is
 * more than a handful of client toggles need.
 */
public final class TreeTabsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch. When off the tab bar never draws and trees behave exactly like vanilla EMI. */
	public static boolean enabled = true;
	/** Opening a tree makes a new tab instead of replacing the current one. Shift inverts this. */
	public static boolean openInNewTab = true;
	/** Write the open tabs to disk so they survive a restart. */
	public static boolean persistTabs = true;
	/** Colour each tab by how much of its tree the player already has. */
	public static boolean showProgress = true;
	/** Draw the bar along the bottom of the tree screen instead of the top. */
	public static boolean barAtBottom = false;
	/** Crafting mode gathers what to make from every tracked tree, not just the visible one. */
	public static boolean aggregateCraftingFavorites = true;
	/**
	 * Refuse to open more than this many tabs at once. Each open tab retains a whole material graph,
	 * so this is the main lever on how much memory the mod holds.
	 */
	public static int maxTabs = 32;
	/**
	 * Cost trees against a shrinking pool so they cannot each claim the same items. Turning this off
	 * gives every tree the full inventory, which double-counts shared materials.
	 */
	public static boolean sharedCraftingInventory = true;
	/** How often, in milliseconds, to recheck each tab's progress. Higher is cheaper. */
	public static int progressIntervalMs = 500;
	/** How many closed tabs Ctrl+Shift+T can bring back. Zero disables the history entirely. */
	public static int closedTabHistory = 16;
	/** Master switch for the Ctrl+Tab / Ctrl+W / Ctrl+D / F2 shortcuts. */
	public static boolean keyboardShortcuts = true;
	/**
	 * Which sidebar shows the crafting list instead of your favourites: NONE, LEFT, RIGHT, TOP or
	 * BOTTOM. Set that sidebar to Favourites in EMI's own settings and this takes it over, giving
	 * you two panels at once. NONE keeps everything in one panel.
	 */
	public static String craftingPanelSide = "NONE";
	/** Group the crafting list into shared materials and one section per tree. */
	public static boolean groupCraftingList = true;
	/** Draw a rule between groups. Costs a row of slots per divider. */
	public static boolean showGroupSeparators = true;
	/** Let group headers be clicked to fold their section away. */
	public static boolean collapsibleGroups = true;
	/**
	 * Whether the favourites sidebar may also carry the crafting list. Turn off to keep favourites
	 * strictly favourites. Ignored anyway once a Crafting page is placed on a sidebar, since the
	 * list would then be showing twice.
	 */
	public static boolean craftingInFavorites = true;

	private TreeTabsConfig() {
	}

	/** Modification time of the file as we last saw it, so outside edits can be detected. */
	private static long lastSeenModified;

	private static Path file() {
		return Platform.configDir().resolve(EmiTreeTabs.MOD_ID + ".json");
	}

	/**
	 * Picks up edits made to the json while the game is running. Cheap enough to call on a timer:
	 * a stat of one small file, and nothing further unless the timestamp moved.
	 */
	public static void reloadIfChanged() {
		Path path = file();
		try {
			if (!Files.exists(path)) {
				return;
			}
			long modified = Files.getLastModifiedTime(path).toMillis();
			if (modified == lastSeenModified) {
				return;
			}
			lastSeenModified = modified;
			if (read(path)) {
				EmiTreeTabs.LOGGER.info("[emitreetabs] reloaded {} after an outside edit", path.getFileName());
			}
		} catch (Exception e) {
			// A failed stat or read must not spam or kill the tick loop.
			lastSeenModified = 0L;
		}
	}

	public static void load() {
		Path path = file();
		if (!Files.exists(path)) {
			save();
			return;
		}
		if (read(path)) {
			// Write back so a file from an older version gains the keys added since, instead of
			// leaving new options invisible and looking unavailable.
			save();
		}
	}

	/** @return true when the file was read successfully. */
	private static boolean read(Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject obj = GSON.fromJson(reader, JsonObject.class);
			if (obj == null) {
				return false;
			}
			enabled = bool(obj, "enabled", enabled);
			openInNewTab = bool(obj, "openInNewTab", openInNewTab);
			persistTabs = bool(obj, "persistTabs", persistTabs);
			showProgress = bool(obj, "showProgress", showProgress);
			barAtBottom = bool(obj, "barAtBottom", barAtBottom);
			aggregateCraftingFavorites = bool(obj, "aggregateCraftingFavorites", aggregateCraftingFavorites);
			maxTabs = clamp(integer(obj, "maxTabs", maxTabs), 1, 256);
			sharedCraftingInventory = bool(obj, "sharedCraftingInventory", sharedCraftingInventory);
			progressIntervalMs = clamp(integer(obj, "progressIntervalMs", progressIntervalMs), 100, 60_000);
			closedTabHistory = clamp(integer(obj, "closedTabHistory", closedTabHistory), 0, 128);
			keyboardShortcuts = bool(obj, "keyboardShortcuts", keyboardShortcuts);
			craftingPanelSide = string(obj, "craftingPanelSide", craftingPanelSide);
			groupCraftingList = bool(obj, "groupCraftingList", groupCraftingList);
			showGroupSeparators = bool(obj, "showGroupSeparators", showGroupSeparators);
			collapsibleGroups = bool(obj, "collapsibleGroups", collapsibleGroups);
			craftingInFavorites = bool(obj, "craftingInFavorites", craftingInFavorites);
		} catch (Exception e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not read {}, using defaults", path, e);
			// Deliberately not rewriting here: a transient read failure must not replace whatever
			// the player has in the file with defaults.
			return false;
		}
		return true;
	}

	public static void save() {
		JsonObject obj = new JsonObject();
		obj.addProperty("enabled", enabled);
		obj.addProperty("openInNewTab", openInNewTab);
		obj.addProperty("persistTabs", persistTabs);
		obj.addProperty("showProgress", showProgress);
		obj.addProperty("barAtBottom", barAtBottom);
		obj.addProperty("aggregateCraftingFavorites", aggregateCraftingFavorites);
		obj.addProperty("maxTabs", maxTabs);
		obj.addProperty("sharedCraftingInventory", sharedCraftingInventory);
		obj.addProperty("progressIntervalMs", progressIntervalMs);
		obj.addProperty("closedTabHistory", closedTabHistory);
		obj.addProperty("keyboardShortcuts", keyboardShortcuts);
		obj.addProperty("craftingPanelSide", craftingPanelSide);
		obj.addProperty("groupCraftingList", groupCraftingList);
		obj.addProperty("showGroupSeparators", showGroupSeparators);
		obj.addProperty("collapsibleGroups", collapsibleGroups);
		obj.addProperty("craftingInFavorites", craftingInFavorites);
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(obj, writer);
			}
			lastSeenModified = Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not write {}", path, e);
		}
	}

	private static boolean bool(JsonObject obj, String key, boolean fallback) {
		return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsBoolean() : fallback;
	}

	private static String string(JsonObject obj, String key, String fallback) {
		return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : fallback;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int integer(JsonObject obj, String key, int fallback) {
		return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
	}
}
