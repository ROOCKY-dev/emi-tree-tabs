package dev.roocky.emitreetabs.tab;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.ui.TreeScreenHooks;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.bom.ProgressState;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.roocky.emitreetabs.sidebar.CraftingGroups;
import dev.roocky.emitreetabs.sidebar.CraftingSidebarType;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.input.EmiInput;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import dev.roocky.emitreetabs.platform.Platform;

/**
 * The list of open trees and the single source of truth for which one EMI is currently showing.
 *
 * <p>Everything funnels through {@link #setActiveTree}: EMI only ever looks at {@code BoM.tree}, so
 * switching tabs is just pointing that field at a different tree and asking the open screen to lay
 * itself out again.
 */
public final class TreeTabs {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final List<TreeTab> TABS = new ArrayList<>();
	private static final Deque<JsonObject> CLOSED = new ArrayDeque<>();

	private static int active = -1;
	/** Tabs read off disk that could not be rebuilt yet because recipes were not loaded. */
	private static List<JsonObject> pending;
	private static int pendingActive;
	private static boolean readFromDisk;
	private static long lastProgressPoll;
	private static EmiPlayerInventory lastPollInventory;
	private static int lastPolledStructure = -1;
	private static boolean dirty;
	private static boolean refreshing;
	/** Bumped whenever tabs are added, removed, reordered or re-costed, to invalidate the poll. */
	private static int structureVersion;

	private TreeTabs() {
	}

	// ------------------------------------------------------------------ state

	public static List<TreeTab> tabs() {
		return Collections.unmodifiableList(TABS);
	}

	public static int count() {
		return TABS.size();
	}

	public static int activeIndex() {
		return active;
	}

	public static TreeTab activeTab() {
		return active >= 0 && active < TABS.size() ? TABS.get(active) : null;
	}

	public static TreeTab tab(int index) {
		return index >= 0 && index < TABS.size() ? TABS.get(index) : null;
	}

	public static boolean hasClosedTabs() {
		return !CLOSED.isEmpty();
	}

	// ------------------------------------------------------------- mutations

	/**
	 * Called after EMI has replaced {@code BoM.tree} through {@code BoM.setGoal}. The new tree is
	 * already built; all we decide is whether it lands in a fresh tab or overwrites the current one.
	 */
	public static void onGoalSet() {
		if (!TreeTabsConfig.enabled) {
			return;
		}
		MaterialTree tree = BoM.tree;
		if (tree == null) {
			return;
		}
		// Shift inverts whatever the configured default is, the way shift-clicking a link does.
		boolean newTab = TreeTabsConfig.openInNewTab != EmiInput.isShiftDown();
		// Ctrl opens the tree ready to build rather than to read. Without this you always land in
		// viewing mode and have to flip it, which is one step too many when the reason you opened
		// the tree was to make the thing.
		boolean startCrafting = EmiInput.isControlDown();

		if (activeTab() == null) {
			TABS.add(new TreeTab(tree));
			active = TABS.size() - 1;
		} else if (newTab && TABS.size() < TreeTabsConfig.maxTabs) {
			captureViewport();
			TABS.add(active + 1, new TreeTab(tree));
			active++;
		} else {
			TreeTab tab = TABS.get(active);
			tab.tree = tree;
			tab.customName = null;
			tab.snapshot = null;
			tab.viewportSet = false;
			tab.labelVersion++;
		}
		if (startCrafting) {
			TreeTab opened = activeTab();
			if (opened != null) {
				opened.craftingMode = true;
				BoM.craftingMode = true;
			}
		}
		markDirty();
	}

	/**
	 * Safety net for trees that appeared without going through {@code setGoal} (another addon, or a
	 * tree that survived from before this mod loaded). Keeps the bar honest about what EMI is showing.
	 */
	public static void adoptCurrentTree() {
		if (!TreeTabsConfig.enabled) {
			return;
		}
		MaterialTree tree = BoM.tree;
		if (tree == null) {
			return;
		}
		for (int i = 0; i < TABS.size(); i++) {
			if (TABS.get(i).tree == tree) {
				active = i;
				return;
			}
		}
		TABS.add(new TreeTab(tree));
		active = TABS.size() - 1;
		markDirty();
	}

	public static void select(int index) {
		if (index < 0 || index >= TABS.size() || index == active) {
			return;
		}
		captureViewport();
		active = index;
		setActiveTree();
		markDirty();
	}

	public static void selectRelative(int delta) {
		if (TABS.size() < 2) {
			return;
		}
		int next = Math.floorMod(active + delta, TABS.size());
		select(next);
	}

	public static void close(int index) {
		if (index < 0 || index >= TABS.size()) {
			return;
		}
		TreeTab previouslyActive = activeTab();
		TreeTab removed = TABS.remove(index);

		JsonObject snapshot = TabCodec.save(removed);
		if (snapshot != null && TreeTabsConfig.closedTabHistory > 0) {
			CLOSED.push(snapshot);
			while (CLOSED.size() > TreeTabsConfig.closedTabHistory) {
				CLOSED.removeLast();
			}
		}

		if (removed == previouslyActive) {
			active = TABS.isEmpty() ? -1 : Math.min(index, TABS.size() - 1);
			setActiveTree();
		} else {
			active = TABS.indexOf(previouslyActive);
		}
		markDirty();
	}

	/** Ctrl+Shift+T. Rebuilds the most recently closed tab from its snapshot. */
	public static void reopenClosed() {
		JsonObject snapshot = CLOSED.poll();
		if (snapshot == null) {
			return;
		}
		TreeTab tab = TabCodec.load(snapshot);
		if (tab == null) {
			return;
		}
		captureViewport();
		int at = active < 0 ? TABS.size() : Math.min(active + 1, TABS.size());
		TABS.add(at, tab);
		active = at;
		setActiveTree();
		markDirty();
	}

	/**
	 * Forks a tab: same goal and same resolutions, but an independent tree. Useful for comparing two
	 * routes to the same item without losing the first one.
	 */
	public static void duplicate(int index) {
		TreeTab source = tab(index);
		if (source == null || TABS.size() >= TreeTabsConfig.maxTabs) {
			return;
		}
		JsonObject snapshot = TabCodec.save(source);
		if (snapshot == null) {
			return;
		}
		TreeTab copy = TabCodec.load(snapshot.deepCopy());
		if (copy == null) {
			return;
		}
		String base = source.customName != null ? source.customName : source.goalName().getString();
		copy.customName = Component.translatable("emi.tree_tabs.copy_suffix", base).getString();
		captureViewport();
		TABS.add(index + 1, copy);
		active = index + 1;
		setActiveTree();
		markDirty();
	}

	public static void move(int from, int to) {
		if (from == to || from < 0 || from >= TABS.size()) {
			return;
		}
		to = Math.max(0, Math.min(to, TABS.size() - 1));
		TreeTab previouslyActive = activeTab();
		TABS.add(to, TABS.remove(from));
		active = TABS.indexOf(previouslyActive);
		markDirty();
	}

	/** Flips one tree between viewing and crafting without closing or switching to it. */
	public static void toggleCrafting(int index) {
		TreeTab tab = tab(index);
		if (tab == null) {
			return;
		}
		tab.craftingMode = !tab.craftingMode;
		if (index == active) {
			BoM.craftingMode = tab.craftingMode;
		}
		markDirty();
	}

	/**
	 * Puts every tab into viewing or crafting at once.
	 *
	 * <p>Flipping a dozen trees one Ctrl+click at a time is the kind of chore that stops people
	 * using crafting mode at all.
	 *
	 * @return the mode everything was switched to.
	 */
	public static boolean toggleAllCrafting() {
		// If anything is still only being viewed, the useful action is to start crafting all of it;
		// only when everything is already crafting does this mean "stop".
		boolean anyViewing = false;
		for (TreeTab tab : TABS) {
			if (!tab.craftingMode) {
				anyViewing = true;
				break;
			}
		}
		setAllCrafting(anyViewing);
		return anyViewing;
	}

	public static void setAllCrafting(boolean crafting) {
		for (TreeTab tab : TABS) {
			tab.craftingMode = crafting;
		}
		TreeTab active = activeTab();
		BoM.craftingMode = active != null && active.craftingMode;
		markDirty();
	}

	/** @return how many tabs are currently being crafted rather than just viewed. */
	public static int craftingCount() {
		int n = 0;
		for (TreeTab tab : TABS) {
			if (tab.craftingMode) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Copies EMI's global mode flag onto the active tab.
	 *
	 * <p>EMI flips {@code BoM.craftingMode} from its own mode button inside {@code mouseClicked};
	 * reading the flag once a frame is sturdier than trying to intercept that specific click.
	 */
	public static void syncActiveCraftingMode() {
		TreeTab tab = activeTab();
		if (tab != null && tab.craftingMode != BoM.craftingMode) {
			tab.craftingMode = BoM.craftingMode;
			markDirty();
		}
	}

	public static void rename(int index, String name) {
		TreeTab tab = tab(index);
		if (tab == null) {
			return;
		}
		tab.customName = name == null || name.isBlank() ? null : name.trim();
		tab.labelVersion++;
		markDirty();
	}

	/** Points EMI at the active tab's tree and makes the open screen redraw it. */
	private static void setActiveTree() {
		TreeTab tab = activeTab();
		BoM.tree = tab == null ? null : tab.tree;
		// Each tab remembers whether you were viewing or building it.
		BoM.craftingMode = tab != null && tab.craftingMode;
		applyViewport();
		recalculate();
	}

	// -------------------------------------------------------------- viewport

	private static TreeScreenHooks screen() {
		Minecraft client = Minecraft.getInstance();
		return client.screen instanceof TreeScreenHooks hooks ? hooks : null;
	}

	public static void captureViewport() {
		TreeScreenHooks screen = screen();
		TreeTab tab = activeTab();
		if (screen == null || tab == null) {
			return;
		}
		tab.offX = screen.emitreetabs$offX();
		tab.offY = screen.emitreetabs$offY();
		tab.zoom = screen.emitreetabs$zoom();
		tab.viewportSet = true;
	}

	public static void applyViewport() {
		TreeScreenHooks screen = screen();
		TreeTab tab = activeTab();
		if (screen == null) {
			return;
		}
		if (tab != null && tab.viewportSet) {
			screen.emitreetabs$offX(tab.offX);
			screen.emitreetabs$offY(tab.offY);
			screen.emitreetabs$zoom(tab.zoom);
		} else {
			// Same framing EMI picks for a freshly opened tree.
			screen.emitreetabs$offX(0);
			screen.emitreetabs$offY(Minecraft.getInstance().screen.height / -3.0);
			screen.emitreetabs$zoom(0);
		}
	}

	public static void recalculate() {
		TreeScreenHooks screen = screen();
		if (screen != null) {
			screen.emitreetabs$recalculate();
		}
	}

	// -------------------------------------------------------------- progress

	/**
	 * Recomputes how far along each tree is against the player's inventory. The active tree is left
	 * alone because EMI already computed it this frame, and recomputing it would clobber the cost
	 * list the screen is about to draw.
	 */
	/**
	 * Recomputes how far along each tree is against the player's inventory.
	 *
	 * <p>Costed carefully, because this walks whole material graphs: it runs at most every
	 * {@code progressIntervalMs}, only for the tabs actually on screen, and only when either the
	 * inventory or the tab structure has actually changed since last time. A player standing still
	 * with the screen open does no work at all.
	 *
	 * <p>The active tree is skipped because EMI already computed it this frame, and recomputing
	 * would clobber the cost list the screen is about to draw.
	 */
	public static void pollProgress(int firstVisible, int lastVisible) {
		if (!TreeTabsConfig.showProgress || TABS.isEmpty()) {
			return;
		}
		long now = Util.getMillis();
		if (now - lastProgressPoll < TreeTabsConfig.progressIntervalMs) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		lastProgressPoll = now;

		EmiPlayerInventory inventory = EmiPlayerInventory.of(player);
		boolean sameInventory = lastPollInventory != null && inventory.isEqual(lastPollInventory);
		if (sameInventory && structureVersion == lastPolledStructure) {
			return;
		}
		lastPollInventory = inventory;
		lastPolledStructure = structureVersion;

		int from = Math.max(0, firstVisible);
		int to = Math.min(TABS.size() - 1, lastVisible);
		for (int i = from; i <= to; i++) {
			TreeTab tab = TABS.get(i);
			if (tab.tree == null || tab.tree.goal == null) {
				continue;
			}
			if (i != active) {
				tab.tree.calculateProgress(inventory);
			}
			tab.progress = tab.tree.goal.progress == null ? ProgressState.UNSTARTED : tab.tree.goal.progress;
		}
	}

	// ----------------------------------------------------------- persistence

	/**
	 * Every tab we know about as json: the ones still waiting on recipes, then the live ones.
	 *
	 * <p>Both halves matter. If a restore could not resolve yet, the unresolved snapshots have to
	 * survive the next write or they are lost forever; and any tab the player opened in the meantime
	 * has to be written too, rather than being dropped in favour of the stale pending list.
	 */
	private static List<JsonObject> snapshotAll() {
		List<JsonObject> all = new ArrayList<>();
		if (pending != null) {
			all.addAll(pending);
		}
		for (TreeTab tab : TABS) {
			JsonObject saved = TabCodec.save(tab);
			if (saved != null) {
				all.add(saved);
			}
		}
		// Repeated failed restores must not let this grow without bound.
		if (all.size() > TreeTabsConfig.maxTabs) {
			all = new ArrayList<>(all.subList(0, TreeTabsConfig.maxTabs));
		}
		return all;
	}

	/**
	 * Drops every live tree, keeping only its json.
	 *
	 * <p>Called when leaving a world. A {@code MaterialTree} holds {@code EmiRecipe} objects, and
	 * those belong to the world that just went away — keeping them alive at the main menu would pin
	 * a whole dead recipe graph. The tabs come back on the next {@link #onEmiReload()}.
	 */
	public static void releaseTrees() {
		if (TABS.isEmpty() && pending == null) {
			BoM.tree = null;
			return;
		}
		List<JsonObject> snapshots = snapshotAll();
		pending = snapshots;
		pendingActive = Math.max(0, active);
		TABS.clear();
		active = -1;
		BoM.tree = null;
		// These hold EmiRecipe references too, and EMI rebuilds them on demand.
		EmiFavorites.syntheticFavorites.clear();
		// The sections hold the same synthetic entries, so they pin recipes just as hard.
		CraftingGroups.begin();
		lastPollInventory = null;
		markDirty();
		flush();
	}

	/**
	 * Rebuilds every tab from json. Called whenever EMI reloads, because a reload throws away every
	 * {@code EmiRecipe} instance the old trees were pointing at.
	 */
	public static void onEmiReload() {
		if (!TreeTabsConfig.enabled) {
			return;
		}
		CraftingFavorites.installSidebarView();
		if (!readFromDisk) {
			readFromDisk = true;
			readFile();
		}
		// Every live tree points at recipe objects this reload just invalidated, so demote them all
		// back to json and rebuild once EMI has finished.
		List<JsonObject> snapshots = snapshotAll();
		int wanted = pending != null ? pendingActive : active;
		TABS.clear();
		active = -1;
		BoM.tree = null;
		pending = snapshots;
		pendingActive = Math.max(0, wanted);
		tryRestore();
	}

	/**
	 * Rebuilds pending tabs, but only once EMI says it has finished loading.
	 *
	 * <p>{@code BoM.reload()} fires while EMI is still building its recipe index, so looking a
	 * recipe up there returns null for recipes that do exist — which previously threw the tab away
	 * for good. Nothing is dropped until {@link EmiReloadManager#isLoaded()} is true and the lookup
	 * has had a fair chance.
	 */
	public static void tryRestore() {
		if (!TreeTabsConfig.enabled || pending == null) {
			return;
		}
		if (!EmiReloadManager.isLoaded()) {
			return;
		}
		List<TreeTab> rebuilt = new ArrayList<>();
		for (JsonObject saved : pending) {
			TreeTab tab = TabCodec.load(saved);
			if (tab != null) {
				rebuilt.add(tab);
			}
		}
		int wanted = pendingActive;
		pending = null;
		TABS.clear();
		TABS.addAll(rebuilt);
		active = TABS.isEmpty() ? -1 : Math.max(0, Math.min(wanted, TABS.size() - 1));
		BoM.tree = activeTab() == null ? null : activeTab().tree;
	}

	private static Path file() {
		return Platform.configDir().resolve(EmiTreeTabs.MOD_ID + "-tabs.json");
	}

	private static void readFile() {
		if (!TreeTabsConfig.persistTabs) {
			return;
		}
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null || !root.has("tabs")) {
				return;
			}
			List<JsonObject> loaded = new ArrayList<>();
			for (JsonElement element : root.getAsJsonArray("tabs")) {
				if (element.isJsonObject()) {
					loaded.add(element.getAsJsonObject());
				}
			}
			pending = loaded;
			pendingActive = root.has("active") ? root.get("active").getAsInt() : 0;
		} catch (Exception e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not read {}", path, e);
		}
	}

	/**
	 * Marks the tabs as needing a write, and invalidates the progress cache.
	 *
	 * <p>Switching tabs used to write the whole json file synchronously on every click. Now the
	 * write is deferred to {@link #flush()}, which the tree screen calls when it closes.
	 */
	public static void markDirty() {
		structureVersion++;
		dirty = true;
		refreshCraftingList();
	}

	/**
	 * Rebuilds the crafting sidebar immediately.
	 *
	 * <p>EMI only calls {@code updateSynthetic} when the player's <em>inventory</em> changes, so
	 * closing a tab or flipping one out of crafting mode left the sidebar showing materials for
	 * trees that were no longer being built. Nothing about the inventory changed, so nothing asked
	 * for a recount.
	 */
	public static void refreshCraftingList() {
		if (refreshing) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		refreshing = true;
		try {
			EmiFavorites.updateSynthetic(EmiPlayerInventory.of(client.player));
			// The sidebar batches its stacks, so the list alone is not enough; the panel has to be
			// told to rebuild too.
			if (CraftingSidebarType.TYPE != null) {
				EmiScreenManager.repopulatePanels(CraftingSidebarType.TYPE);
			}
			EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
		} catch (Throwable t) {
			EmiTreeTabs.LOGGER.debug("[emitreetabs] could not refresh the crafting sidebar", t);
		} finally {
			refreshing = false;
		}
	}

	/** Writes the tabs to disk if anything changed. */
	public static void flush() {
		if (!dirty) {
			return;
		}
		dirty = false;
		writeFile();
	}

	private static void writeFile() {
		if (!TreeTabsConfig.persistTabs) {
			return;
		}
		JsonArray array = new JsonArray();
		for (JsonObject saved : snapshotAll()) {
			array.add(saved);
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.addProperty("active", pending != null ? pendingActive : Math.max(0, active));
		root.add("tabs", array);

		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not write {}", path, e);
		}
	}
}
