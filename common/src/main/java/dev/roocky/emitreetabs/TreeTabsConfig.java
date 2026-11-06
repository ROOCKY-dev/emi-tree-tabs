package dev.roocky.emitreetabs;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.roocky.emitreetabs.platform.Platform;


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

	/**
	 * Where the tabs live: {@code auto}, {@code horizontal} or {@code vertical}.
	 *
	 * <p>Auto picks the sidebar whenever the screen can afford a fifth of its width and a few rows,
	 * and falls back to the strip when it cannot. A screen that cannot host the sidebar is not a
	 * rare case — GUI scale 4 on a small window is common — so the fallback has to be real rather
	 * than theoretical.
	 */
	public static String tabOrientation = "auto";
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
	 * Frame a crafting-list entry that is a tag or any other choice of items, so it cannot be
	 * mistaken for the plain item a different tree happens to want.
	 */
	public static boolean markChoiceEntries = true;
	/**
	 * Count materials another mod says you have elsewhere — in chests, drawers, a backpack —
	 * towards the crafting list.
	 *
	 * <p>Off by design, not by caution. Turning it on changes what the list asks you to gather on
	 * the strength of another mod's idea of what you own, and doing that without being asked is how
	 * a tool stops being trustworthy. Nothing happens either way until such a mod is installed.
	 */
	public static boolean useExternalStock = false;
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

	/**
	 * Applies every valid key in the file, keeping the current value for any key that is missing or
	 * malformed.
	 *
	 * <p>Key by key rather than all or nothing. The old version assigned fields one after another
	 * and could throw on, say, {@code "maxTabs": "lots"} after half of them were already applied,
	 * then reported "using defaults" while actually running on a mix of the file and the defaults.
	 * Now a bad value costs only that key, and the warning says which key it was. Only a file that
	 * is not a json object at all counts as unreadable, and that is detected before anything is
	 * applied.
	 *
	 * @return true when the file was parsed; false when it could not be, leaving every value as it was.
	 */
	static boolean read(Path path) {
		JsonObject obj;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			obj = GSON.fromJson(reader, JsonObject.class);
		} catch (Exception e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not read {}, keeping the current settings", path, e);
			// Deliberately not rewriting here: a transient read failure must not replace whatever
			// the player has in the file with defaults.
			return false;
		}
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
		tabOrientation = string(obj, "tabOrientation", tabOrientation);
		craftingPanelSide = string(obj, "craftingPanelSide", craftingPanelSide);
		groupCraftingList = bool(obj, "groupCraftingList", groupCraftingList);
		showGroupSeparators = bool(obj, "showGroupSeparators", showGroupSeparators);
		collapsibleGroups = bool(obj, "collapsibleGroups", collapsibleGroups);
		markChoiceEntries = bool(obj, "markChoiceEntries", markChoiceEntries);
		useExternalStock = bool(obj, "useExternalStock", useExternalStock);
		craftingInFavorites = bool(obj, "craftingInFavorites", craftingInFavorites);
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
		obj.addProperty("tabOrientation", tabOrientation);
		obj.addProperty("craftingPanelSide", craftingPanelSide);
		obj.addProperty("groupCraftingList", groupCraftingList);
		obj.addProperty("showGroupSeparators", showGroupSeparators);
		obj.addProperty("collapsibleGroups", collapsibleGroups);
		obj.addProperty("markChoiceEntries", markChoiceEntries);
		obj.addProperty("useExternalStock", useExternalStock);
		obj.addProperty("craftingInFavorites", craftingInFavorites);
		Path path = file();
		try {
			// Replaced whole or not at all, so a failed write cannot leave a truncated config.
			SafeFiles.write(path, writer -> GSON.toJson(obj, writer));
			lastSeenModified = Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not write {}", path, e);
		}
	}

	// Each helper answers "the value in the file, or the fallback if there is no usable one". Gson
	// throws on a value of the wrong shape (a number that is not an int, an object where a string
	// was expected), so the check has to happen here, per key, rather than around the whole read.

	private static boolean bool(JsonObject obj, String key, boolean fallback) {
		JsonPrimitive value = primitive(obj, key);
		if (value == null) {
			return fallback;
		}
		if (value.isBoolean()) {
			return value.getAsBoolean();
		}
		// A quoted "true" was accepted before this check existed, so a hand-edited file keeps working.
		String text = value.getAsString().trim();
		if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
			return Boolean.parseBoolean(text);
		}
		reportBadValue(key, value, "true or false");
		return fallback;
	}

	private static String string(JsonObject obj, String key, String fallback) {
		JsonPrimitive value = primitive(obj, key);
		return value == null ? fallback : value.getAsString();
	}

	private static int integer(JsonObject obj, String key, int fallback) {
		JsonPrimitive value = primitive(obj, key);
		if (value == null) {
			return fallback;
		}
		double parsed;
		try {
			parsed = value.isNumber() ? value.getAsDouble() : Double.parseDouble(value.getAsString().trim());
		} catch (NumberFormatException e) {
			parsed = Double.NaN;
		}
		if (!Double.isFinite(parsed)) {
			reportBadValue(key, value, "a whole number");
			return fallback;
		}
		// Truncated, as Gson's getAsInt did before; clamped first so a huge number cannot wrap
		// round to a negative one.
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, parsed));
	}

	/** @return the key's value when it is present and a plain value, else null. */
	private static JsonPrimitive primitive(JsonObject obj, String key) {
		JsonElement element = obj.get(key);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (!element.isJsonPrimitive()) {
			reportBadValue(key, element, "a plain value");
			return null;
		}
		return element.getAsJsonPrimitive();
	}

	private static void reportBadValue(String key, JsonElement value, String expected) {
		EmiTreeTabs.LOGGER.warn("[emitreetabs] config key '{}' is {}, expected {}; keeping the current value",
				key, value, expected);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
