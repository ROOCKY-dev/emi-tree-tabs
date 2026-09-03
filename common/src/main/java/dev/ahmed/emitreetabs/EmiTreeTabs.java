package dev.ahmed.emitreetabs;

import com.mojang.logging.LogUtils;

import dev.ahmed.emitreetabs.sidebar.CraftingSidebarType;
import org.slf4j.Logger;

/**
 * EMI Tree Tabs - keeps more than one EMI recipe tree alive at a time.
 *
 * <p>EMI stores exactly one {@code MaterialTree} in the static field {@code BoM.tree}, so opening a
 * new tree throws away whatever you were looking at. This mod keeps a list of trees and swaps the
 * right one into {@code BoM.tree} when you change tab, which means EMI's own tree screen keeps
 * doing all of the drawing and editing work unchanged.
 *
 * <p>Loader-agnostic. Each loader module has its own entrypoint that calls {@link #initClient()}
 * and registers its own events.
 */
public final class EmiTreeTabs {
	public static final String MOD_ID = "emitreetabs";
	public static final Logger LOGGER = LogUtils.getLogger();

	/** Cloth Config is optional; without it the json file is still the source of truth. */
	public static final String CLOTH_CONFIG = "cloth_config";

	private EmiTreeTabs() {
	}

	/**
	 * Client-side setup shared by every loader.
	 *
	 * <p>Must run before EMI reads its own config, so that a sidebar page saved as our custom type
	 * resolves on the first launch rather than falling back to NONE.
	 */
	public static void initClient() {
		TreeTabsConfig.load();
		CraftingSidebarType.install();
	}
}
