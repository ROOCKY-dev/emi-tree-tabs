package dev.roocky.emitreetabs.tab;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.roocky.emitreetabs.SafeFiles;
import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.ui.SidebarRows;
import dev.roocky.emitreetabs.ui.TreeScreenHooks;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.MaterialNode;
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
import net.minecraft.client.gui.components.toasts.SystemToast;
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
	private static final List<TabGroup> GROUPS = new ArrayList<>();
	private static int nextGroupId = 1;

	private static int active = -1;
	/** Tabs read off disk that could not be rebuilt yet because recipes were not loaded. */
	private static List<JsonObject> pending;
	private static int pendingActive;
	private static boolean readFromDisk;
	private static long lastProgressPoll;
	private static EmiPlayerInventory lastPollInventory;
	private static int lastPolledStructure = -1;
	/** Bumped when the inventory or tab structure changes; a tab's marker is fresh if it matches. */
	private static int progressGeneration;
	/** Something changed that has not been written to disk yet. Only a successful write clears it. */
	private static boolean dirty;
	/** The last save failed, so the next success is worth saying out loud. */
	private static boolean saveFailing;
	private static boolean refreshing;
	/** The last crafting-list refresh failure, so the same cause is warned about only once. */
	private static String lastRefreshFailure;
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
		} else if (newTab) {
			refuseNewTabAtLimit();
			return;
		} else {
			// Deliberate replacement: the player asked for this tab to be overwritten (Shift, or
			// openInNewTab turned off), so the old tree is meant to go.
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
	 * A new tab was asked for and there is no room: put EMI back on the tree it was showing and
	 * change nothing.
	 *
	 * <p>This used to overwrite the current tab instead, with a toast saying so. The toast was
	 * accurate but came too late: the tree, its name, its resolutions and its batch count were
	 * already gone, with nothing to bring them back. Refusing loses nothing; the toast says how to
	 * make room.
	 *
	 * <p>EMI's {@code setGoal} has already run by now, and all it did was replace {@code BoM.tree}
	 * and clear {@code BoM.craftingMode} (checked against the 1.1.24 bytecode), so restoring those
	 * two fields undoes it completely. The viewport is captured first because EMI's own tree screen
	 * re-runs {@code init()} after a goal change, and that re-applies the stored viewport.
	 */
	private static void refuseNewTabAtLimit() {
		TreeTab current = activeTab();
		captureViewport();
		BoM.tree = current.tree;
		BoM.craftingMode = current.craftingMode;
		showTabLimitToast();
	}

	private static void showTabLimitToast() {
		toast(Component.translatable("emi.tree_tabs.limit.title", TreeTabsConfig.maxTabs),
				Component.translatable("emi.tree_tabs.limit.body"));
	}

	/**
	 * Shows one of this mod's notices.
	 *
	 * <p>The only place a toast is built, because the toast id enum is spelled differently on
	 * Minecraft 1.20.1 ({@code SystemToastIds}) and 1.21.1 ({@code SystemToastId}); keeping it here
	 * keeps that difference to one line between the two branches. One id for every notice: a newer
	 * one replaces the older rather than queueing behind it.
	 */
	private static void toast(Component title, Component body) {
		SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(),
				SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, body);
	}

	/**
	 * Called after EMI changes a default recipe. EMI has already rebuilt {@code BoM.tree}; every
	 * other tab still holds nodes built from the old default until it is rebuilt here.
	 *
	 * <p>A tab that picked its own recipe for an ingredient keeps it: {@code MaterialTree.getRecipe}
	 * checks the tree's own resolutions before the defaults.
	 */
	public static void onDefaultsChanged() {
		if (!TreeTabsConfig.enabled || TABS.isEmpty()) {
			return;
		}
		for (TreeTab tab : TABS) {
			if (tab.tree != null && tab.tree != BoM.tree) {
				tab.tree.recalculate();
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

	/**
	 * Ctrl+Shift+T. Rebuilds the most recently closed tab from its snapshot.
	 *
	 * <p>The snapshot only leaves the history once the tab is actually back. It used to be taken off
	 * first, so a reopen that failed (its recipe gone after a datapack change) threw the entry away,
	 * and a reopen at the tab limit went past {@code maxTabs}.
	 *
	 * <p>An entry that cannot be rebuilt is moved behind the others instead of blocking them: the
	 * next press reaches the older history, and the entry is still there if its recipe comes back.
	 */
	public static void reopenClosed() {
		JsonObject snapshot = CLOSED.peek();
		if (snapshot == null) {
			return;
		}
		if (TABS.size() >= TreeTabsConfig.maxTabs) {
			showTabLimitToast();
			return;
		}
		TreeTab tab = tryLoad(snapshot);
		if (tab == null) {
			CLOSED.poll();
			CLOSED.addLast(snapshot);
			toast(Component.translatable("emi.tree_tabs.reopen_failed.title"),
					Component.translatable("emi.tree_tabs.reopen_failed.body", TabCodec.goalId(snapshot)));
			return;
		}
		CLOSED.poll();
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
		if (source == null) {
			return;
		}
		if (TABS.size() >= TreeTabsConfig.maxTabs) {
			showTabLimitToast();
			return;
		}
		JsonObject snapshot = TabCodec.save(source);
		if (snapshot == null) {
			return;
		}
		TreeTab copy = tryLoad(snapshot.deepCopy());
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
	 * Recomputes how far along each tree is against the player's inventory.
	 *
	 * <p>Costed carefully, because this walks whole material graphs: it runs at most every
	 * {@code progressIntervalMs}, only for the tabs actually on screen, and only for a tab whose
	 * marker is older than the current inventory and tab structure. A player standing still with
	 * the screen open does no work at all.
	 *
	 * <p>Freshness is tracked per tab ({@link TreeTab#progressGeneration}) rather than for the poll
	 * as a whole. With a single "nothing changed" check, scrolling a tab into view that had been
	 * off screen when the inventory last changed left its marker stale until the inventory changed
	 * again, because nothing the check looked at had moved.
	 *
	 * <p>The active tree is not recomputed because EMI already computed it this frame, and
	 * recomputing would clobber the cost list the screen is about to draw; its result is only read.
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
		if (!sameInventory || structureVersion != lastPolledStructure) {
			// Every marker computed before now describes an inventory or a tab list that is gone.
			progressGeneration++;
			lastPollInventory = inventory;
			lastPolledStructure = structureVersion;
		}

		int from = Math.max(0, firstVisible);
		int to = Math.min(TABS.size() - 1, lastVisible);
		for (int i = from; i <= to; i++) {
			TreeTab tab = TABS.get(i);
			if (tab.tree == null || tab.tree.goal == null) {
				continue;
			}
			boolean isActive = i == active;
			if (!isActive && tab.progressGeneration == progressGeneration) {
				continue;
			}
			if (!isActive) {
				tab.tree.calculateProgress(inventory);
			}
			tab.progress = tab.tree.goal.progress == null ? ProgressState.UNSTARTED : tab.tree.goal.progress;
			tab.progressGeneration = progressGeneration;
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
		// Unconditional, and before the early return: a cache can outlive the tabs it was built
		// from, since closing the last tab does not run another aggregation pass to empty it.
		CraftingFavorites.release();
		SubCraftCosts.release();
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
		List<JsonObject> rejected = new ArrayList<>();
		int wanted = pendingActive;
		// Past the end until the wanted tab is reached, so an index beyond the list lands on the last tab.
		int wantedAfterRestore = Integer.MAX_VALUE;
		for (int i = 0; i < pending.size(); i++) {
			if (i == wanted) {
				// Tabs before it may have been rejected, so its position can only move down. If it
				// is rejected itself this lands on the next tab that did come back.
				wantedAfterRestore = rebuilt.size();
			}
			JsonObject saved = pending.get(i);
			TreeTab tab = tryLoad(saved);
			if (tab != null) {
				rebuilt.add(tab);
			} else {
				rejected.add(saved);
			}
		}
		int requested = pending.size();
		pending = null;
		TABS.clear();
		TABS.addAll(rebuilt);
		active = TABS.isEmpty() ? -1 : Math.max(0, Math.min(wantedAfterRestore, TABS.size() - 1));
		BoM.tree = activeTab() == null ? null : activeTab().tree;
		if (!rejected.isEmpty()) {
			keepRejected(rejected, requested);
		}
	}

	/**
	 * Rebuilds one tab, treating any failure as "this tab cannot come back" rather than letting it
	 * abort the caller.
	 *
	 * <p>{@link TabCodec#load} already refuses malformed json without throwing, but a tree is built
	 * by EMI's own code, and an odd recipe from another mod can throw from inside that. One such
	 * tab used to end the whole restore, taking every valid tab after it down too.
	 */
	private static TreeTab tryLoad(JsonObject saved) {
		try {
			return TabCodec.load(saved);
		} catch (RuntimeException e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not rebuild the tab for {}", TabCodec.goalId(saved), e);
			return null;
		}
	}

	private static Path file() {
		return Platform.configDir().resolve(EmiTreeTabs.MOD_ID + "-tabs.json");
	}

	/** Where tabs that could not be restored are kept. See {@link #keepRejected}. */
	private static Path rejectedFile() {
		return Platform.configDir().resolve(EmiTreeTabs.MOD_ID + "-tabs-rejected.json");
	}

	/** Bounds the rejected file, which only grows otherwise. The oldest entries go first. */
	private static final int MAX_REJECTED_KEPT = 64;

	/**
	 * Writes the tabs that could not be restored to their own file, so the next save does not
	 * erase them.
	 *
	 * <p>Usually the goal recipe is simply not loaded: a mod was removed, or this is a server that
	 * does not have it. Before, such a tab vanished at the next save with only an info line in the
	 * log. Whether such tabs should come back by themselves when the recipe returns is a product
	 * decision that has not been taken, so for now they are kept where a player (or a support
	 * thread) can find them, and nothing reads this file back automatically.
	 */
	private static void keepRejected(List<JsonObject> rejected, int requested) {
		EmiTreeTabs.LOGGER.warn("[emitreetabs] restored {} of {} tabs; {} could not be rebuilt",
				requested - rejected.size(), requested, rejected.size());
		if (!TreeTabsConfig.persistTabs) {
			return;
		}
		Path path = rejectedFile();
		JsonArray kept = readRejected(path);
		for (JsonObject tab : rejected) {
			// A rejected tab stays in the main file until the next save, so the next launch can
			// reject the very same entry again. One copy is enough.
			if (!kept.contains(tab)) {
				kept.add(tab);
			}
		}
		while (kept.size() > MAX_REJECTED_KEPT) {
			kept.remove(0);
		}
		JsonObject root = new JsonObject();
		root.addProperty("note", "Tabs EMI Tree Tabs could not restore, usually because their recipe "
				+ "was not loaded. Not read back automatically.");
		root.add("tabs", kept);
		try {
			SafeFiles.write(path, writer -> GSON.toJson(root, writer));
			EmiTreeTabs.LOGGER.warn("[emitreetabs] kept the {} unrestorable tabs in {}", rejected.size(), path);
		} catch (IOException e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not write {}", path, e);
		}
		toast(Component.translatable("emi.tree_tabs.restore_partial.title", rejected.size()),
				Component.translatable("emi.tree_tabs.restore_partial.body", path.getFileName().toString()));
	}

	private static JsonArray readRejected(Path path) {
		if (!Files.exists(path)) {
			return new JsonArray();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root != null && root.has("tabs") && root.get("tabs").isJsonArray()) {
				return root.getAsJsonArray("tabs");
			}
		} catch (Exception e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not read {}, setting it aside", path, e);
			SafeFiles.setAsideCorrupt(path);
		}
		return new JsonArray();
	}

	/**
	 * Loads the saved tabs, falling back to the backup if the file itself cannot be read.
	 *
	 * <p>An unreadable file is moved aside rather than left in place: otherwise the next save would
	 * overwrite it, and the tabs in it would be gone for good. The backup is only consulted when
	 * the file exists but is broken. A missing file means there is nothing to restore (or the
	 * player deleted it to start over), and bringing old tabs back from the backup then would be
	 * surprising rather than helpful.
	 */
	private static void readFile() {
		if (!TreeTabsConfig.persistTabs) {
			return;
		}
		Path path = file();
		if (!Files.exists(path) || readTabsFrom(path)) {
			return;
		}
		Path aside = SafeFiles.setAsideCorrupt(path);
		EmiTreeTabs.LOGGER.warn("[emitreetabs] {} could not be read{}; trying the backup", path,
				aside == null ? "" : " and was moved to " + aside.getFileName());
		Path backup = SafeFiles.backupOf(path);
		if (Files.exists(backup) && readTabsFrom(backup)) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] restored tabs from {}", backup);
		}
	}

	/**
	 * Reads one tabs file into {@link #pending} and {@link #GROUPS}.
	 *
	 * <p>Parsed completely before anything is assigned, so a file that fails part way does not
	 * leave half of it applied. Inside a file that parses, damage is contained to the entry it is
	 * in: a malformed group is skipped, a malformed tab is left for {@link #tryRestore} to reject.
	 *
	 * @return false when the file is not usable at all: not json, or no list of tabs.
	 */
	private static boolean readTabsFrom(Path path) {
		JsonObject root;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			root = GSON.fromJson(reader, JsonObject.class);
		} catch (Exception e) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not parse {}", path, e);
			return false;
		}
		if (root == null) {
			// An empty file. Nothing to restore, and nothing worth keeping either.
			return true;
		}
		if (!root.has("tabs")) {
			return true;
		}
		if (!root.get("tabs").isJsonArray()) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] {} has no list of tabs", path);
			return false;
		}
		List<JsonObject> loaded = new ArrayList<>();
		for (JsonElement element : root.getAsJsonArray("tabs")) {
			if (element.isJsonObject()) {
				loaded.add(element.getAsJsonObject());
			}
		}
		int loadedActive = 0;
		JsonElement activeElement = root.get("active");
		if (activeElement != null && activeElement.isJsonPrimitive() && activeElement.getAsJsonPrimitive().isNumber()) {
			loadedActive = activeElement.getAsInt();
		}

		// Version 1 files have no groups at all, which is a valid state rather than an error:
		// every tab simply loads ungrouped.
		List<TabGroup> loadedGroups = new ArrayList<>();
		if (root.has("groups") && root.get("groups").isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray("groups")) {
				TabGroup group = tryLoadGroup(element);
				if (group != null) {
					loadedGroups.add(group);
				}
			}
		}

		pending = loaded;
		pendingActive = loadedActive;
		GROUPS.clear();
		GROUPS.addAll(loadedGroups);
		nextGroupId = 1;
		for (TabGroup group : loadedGroups) {
			nextGroupId = Math.max(nextGroupId, group.id + 1);
		}
		return true;
	}

	private static TabGroup tryLoadGroup(JsonElement element) {
		if (!element.isJsonObject()) {
			return null;
		}
		try {
			return TabGroup.load(element.getAsJsonObject());
		} catch (RuntimeException e) {
			// A wrongly typed name or colour. The tabs in it fall back to ungrouped, which is how a
			// tab whose group is missing is already shown.
			EmiTreeTabs.LOGGER.warn("[emitreetabs] skipping a malformed phase: {}", element);
			return null;
		}
	}

	// The old recipe-sync apparatus lived here: a mixin on MaterialTree.addResolution, a pending
	// offer, two toasts and a keybind. It is gone. Disagreement between trees is not an event to
	// be caught at the moment it happens - it is a property of the open trees, true until someone
	// fixes it - so it is computed on demand by RecipeChoices and shown only when asked for.

	// ----------------------------------------------------------------- groups

	public static List<TabGroup> groups() {
		return Collections.unmodifiableList(GROUPS);
	}

	public static TabGroup group(int id) {
		for (TabGroup g : GROUPS) {
			if (g.id == id) {
				return g;
			}
		}
		return null;
	}

	public static TabGroup createGroup(String name) {
		TabGroup group = new TabGroup(nextGroupId++, name == null || name.isBlank()
				? Component.translatable("emi.tree_tabs.group.default", GROUPS.size() + 1).getString()
				: name, TabGroup.colourFor(GROUPS.size()));
		GROUPS.add(group);
		markDirty();
		return group;
	}

	/**
	 * Makes a phase out of one tree.
	 *
	 * <p>The only way a group gets created, and it is deliberately not "make an empty group": an
	 * empty phase shows as a header with nothing under it, which looks broken rather than new.
	 * Starting it with the tree you were looking at means the thing you just made is visible.
	 *
	 * @return the new group, or null when there is no tab to put in it
	 */
	public static TabGroup groupTab(int tabIndex) {
		TreeTab tab = tab(tabIndex);
		if (tab == null) {
			return null;
		}
		TabGroup group = createGroup(null);
		tab.groupId = group.id;
		markDirty();
		return group;
	}

	public static void renameGroup(int id, String name) {
		TabGroup group = group(id);
		if (group == null) {
			return;
		}
		group.name = name == null || name.isBlank()
				? Component.translatable("emi.tree_tabs.group.default", GROUPS.indexOf(group) + 1)
						.getString()
				: name.trim();
		markDirty();
	}

	/**
	 * Drops a phase, leaving its trees loose rather than closing them.
	 *
	 * <p>Closing them would be the destructive reading of "delete the group", and the group is a
	 * label on trees rather than a container for them.
	 */
	public static void removeGroup(int id) {
		if (GROUPS.removeIf(g -> g.id == id)) {
			for (TreeTab tab : TABS) {
				if (tab.groupId == id) {
					tab.groupId = -1;
				}
			}
			markDirty();
		}
	}

	public static void assignToGroup(int tabIndex, int groupId) {
		TreeTab tab = tab(tabIndex);
		if (tab == null || tab.groupId == groupId) {
			return;
		}
		tab.groupId = groupId >= 0 && group(groupId) != null ? groupId : -1;
		markDirty();
	}

	public static void setCollapsed(int groupId, boolean collapsed) {
		TabGroup g = group(groupId);
		if (g != null && g.collapsed != collapsed) {
			g.collapsed = collapsed;
			markDirty();
		}
	}

	/**
	 * Sets a group aside, or brings it back.
	 *
	 * <p>Parking does not touch the tabs' own crafting flags, so unparking restores exactly the
	 * state you had rather than a guess at it.
	 */
	public static void setParked(int groupId, boolean parked) {
		TabGroup g = group(groupId);
		if (g != null && g.parked != parked) {
			g.parked = parked;
			markDirty();
		}
	}

	/** The one-click version of the workflow: work on this phase, set every other one aside. */
	public static void parkAllExcept(int groupId) {
		boolean changed = false;
		for (TabGroup g : GROUPS) {
			boolean want = g.id != groupId;
			if (g.parked != want) {
				g.parked = want;
				changed = true;
			}
		}
		if (changed) {
			markDirty();
		}
	}

	/** True when this tab's group is parked, and so it should not feed the crafting list. */
	public static boolean isParked(TreeTab tab) {
		if (tab == null || tab.groupId < 0) {
			return false;
		}
		TabGroup g = group(tab.groupId);
		return g != null && g.parked;
	}

	/** Sets crafting mode for every tab in one group, leaving the rest alone. */
	public static void setGroupCrafting(int groupId, boolean crafting) {
		boolean changed = false;
		for (TreeTab tab : TABS) {
			if (tab.groupId == groupId && tab.craftingMode != crafting) {
				tab.craftingMode = crafting;
				changed = true;
			}
		}
		if (changed) {
			TreeTab active = activeTab();
			BoM.craftingMode = active != null && active.craftingMode;
			markDirty();
		}
	}

	public static int groupCraftingCount(int groupId) {
		int n = 0;
		for (TreeTab tab : TABS) {
			if (tab.groupId == groupId && tab.craftingMode) {
				n++;
			}
		}
		return n;
	}

	public static int groupSize(int groupId) {
		int n = 0;
		for (TreeTab tab : TABS) {
			if (tab.groupId == groupId) {
				n++;
			}
		}
		return n;
	}

	/** The sidebar's rows, and the mapping back from a row to the tab it stands for. */
	public static SidebarRows.Result sidebarRows() {
		List<SidebarRows.GroupRef> gs = new ArrayList<>();
		for (TabGroup g : GROUPS) {
			gs.add(new SidebarRows.GroupRef(g.id, g.collapsed));
		}
		List<SidebarRows.TabRef> ts = new ArrayList<>();
		for (int i = 0; i < TABS.size(); i++) {
			ts.add(new SidebarRows.TabRef(i, TABS.get(i).groupId));
		}
		return SidebarRows.build(gs, ts);
	}

	/**
	 * Sets how many times a tree is being made, and recosts it.
	 *
	 * <p>Clamped rather than trusted: {@link Formula} already refuses absurd values, but this is the
	 * only path that writes the field and a batch count of zero or a billion makes costing the tree
	 * either pointless or endless.
	 */
	public static void setBatches(int index, long batches) {
		TreeTab tab = tab(index);
		if (tab == null || tab.tree == null) {
			return;
		}
		long clamped = Math.max(1, Math.min(batches, Formula.MAX));
		if (tab.tree.batches == clamped) {
			return;
		}
		tab.tree.batches = clamped;
		tab.tree.recalculate();
		tab.labelVersion++;
		markDirty();
	}
	/**
	 * Marks the tabs as needing a write, invalidates the progress cache and recounts the crafting
	 * list: the one "something changed" call every mutation ends with.
	 *
	 * <p>The write itself is deferred to {@link #flush()}, which the tree screen calls when it
	 * closes. Switching tabs used to write the whole json file synchronously on every click.
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
			lastRefreshFailure = null;
		} catch (Throwable t) {
			reportRefreshFailure(t);
		} finally {
			refreshing = false;
		}
	}

	/**
	 * Warns once per distinct cause when the crafting list could not be rebuilt.
	 *
	 * <p>This used to log at debug level only, so a crafting list that had silently stopped
	 * updating had no explanation in a normal log. Every mutation refreshes, so warning every time
	 * would flood the log instead; the same failure repeating is logged at debug.
	 */
	private static void reportRefreshFailure(Throwable t) {
		String cause = t.getClass().getName() + ": " + t.getMessage();
		if (cause.equals(lastRefreshFailure)) {
			EmiTreeTabs.LOGGER.debug("[emitreetabs] could not refresh the crafting list again", t);
			return;
		}
		lastRefreshFailure = cause;
		EmiTreeTabs.LOGGER.warn("[emitreetabs] could not refresh the crafting list; it may be out of "
				+ "date until the next change", t);
	}

	/**
	 * Writes the tabs to disk if anything changed since the last successful write.
	 *
	 * <p>The dirty flag is cleared only once the file is really written. It used to be cleared
	 * first, so one failed write (a locked or read-only config folder) meant no later flush would
	 * try again unless the player changed something else. Retrying needs no timer: flush already
	 * runs on discrete events (closing the tree screen, leaving a world), never per frame.
	 *
	 * <p>With {@code persistTabs} off there is nothing to write, which counts as success.
	 */
	public static void flush() {
		if (!dirty) {
			return;
		}
		if (writeFile()) {
			dirty = false;
		}
	}

	/** @return true when the tabs are safely on disk, or persistence is off. */
	private static boolean writeFile() {
		if (!TreeTabsConfig.persistTabs) {
			return true;
		}
		JsonArray array = new JsonArray();
		for (JsonObject saved : snapshotAll()) {
			array.add(saved);
		}
		JsonObject root = new JsonObject();
		// 2 added groups. A version 1 file still loads; it simply has none.
		root.addProperty("version", 2);
		root.addProperty("active", pending != null ? pendingActive : Math.max(0, active));
		root.add("tabs", array);
		if (!GROUPS.isEmpty()) {
			JsonArray groups = new JsonArray();
			for (TabGroup g : GROUPS) {
				groups.add(g.save());
			}
			root.add("groups", groups);
		}

		Path path = file();
		try {
			SafeFiles.write(path, writer -> GSON.toJson(root, writer));
		} catch (IOException e) {
			reportSaveFailure(path, e);
			return false;
		}
		if (saveFailing) {
			saveFailing = false;
			EmiTreeTabs.LOGGER.info("[emitreetabs] saved {} again after earlier failures", path.getFileName());
		}
		return true;
	}

	/**
	 * Tells the player once per run of failures, not once per attempt.
	 *
	 * <p>The tabs are still in memory and the next flush retries, so this is a warning that work
	 * would be lost on a restart, not that it is lost now.
	 */
	private static void reportSaveFailure(Path path, IOException e) {
		if (saveFailing) {
			EmiTreeTabs.LOGGER.debug("[emitreetabs] still could not write {}", path, e);
			return;
		}
		saveFailing = true;
		EmiTreeTabs.LOGGER.warn("[emitreetabs] could not write {}; the tabs are kept in memory and "
				+ "saving will be retried", path, e);
		toast(Component.translatable("emi.tree_tabs.save_failed.title"),
				Component.translatable("emi.tree_tabs.save_failed.body"));
	}
}
