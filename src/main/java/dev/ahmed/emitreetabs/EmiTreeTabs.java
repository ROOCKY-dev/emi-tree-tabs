package dev.ahmed.emitreetabs;

import com.mojang.logging.LogUtils;

import dev.ahmed.emitreetabs.sidebar.CraftingSidebarType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * EMI Tree Tabs - keeps more than one EMI recipe tree alive at a time.
 *
 * <p>EMI stores exactly one {@code MaterialTree} in the static field {@code BoM.tree}, so opening a
 * new tree throws away whatever you were looking at. This mod keeps a list of trees and swaps the
 * right one into {@code BoM.tree} when you change tab, which means EMI's own tree screen keeps
 * doing all of the drawing and editing work unchanged.
 */
@Mod(EmiTreeTabs.MOD_ID)
public class EmiTreeTabs {
	public static final String MOD_ID = "emitreetabs";
	public static final Logger LOGGER = LogUtils.getLogger();

	/** Cloth Config is optional; without it the json file is still the source of truth. */
	private static final String CLOTH = "cloth_config";

	public EmiTreeTabs() {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		TreeTabsConfig.load();
		// Before EMI reads its own config, so a saved page of our type resolves on the first launch.
		CraftingSidebarType.install();
		registerConfigScreen();
	}

	/**
	 * Adds the Config button to this mod's entry in the Mods list.
	 *
	 * <p>Guarded on Cloth Config actually being loaded: {@link ClothConfigScreen} is the only class
	 * that touches Cloth types, and it is never referenced unless we get past this check, so the
	 * mod runs fine without the library present.
	 */
	private void registerConfigScreen() {
		if (!ModList.get().isLoaded(CLOTH)) {
			LOGGER.info("[emitreetabs] {} not present, edit config/{}.json by hand", CLOTH, MOD_ID);
			return;
		}
		try {
			ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new ConfigScreenHandler.ConfigScreenFactory(
							(client, parent) -> ClothConfigScreen.create(parent)));
		} catch (Throwable t) {
			// A config button is not worth taking the game down for.
			LOGGER.warn("[emitreetabs] could not register the config screen", t);
		}
	}
}
