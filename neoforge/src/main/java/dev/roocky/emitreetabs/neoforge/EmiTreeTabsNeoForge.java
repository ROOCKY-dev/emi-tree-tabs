package dev.roocky.emitreetabs.neoforge;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.roocky.emitreetabs.config.YaclConfigScreen;
import dev.roocky.emitreetabs.ui.ConfigScreenHook;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge entrypoint. Everything of substance lives in the shared module.
 *
 * <p>{@code dist = CLIENT} on the annotation replaces the old {@code FMLEnvironment.dist} check:
 * NeoForge does not construct this class at all on a dedicated server.
 */
@Mod(value = EmiTreeTabs.MOD_ID, dist = Dist.CLIENT)
public class EmiTreeTabsNeoForge {

	public EmiTreeTabsNeoForge(ModContainer container) {
		EmiTreeTabs.initClient();
		registerConfigScreen(container);
	}

	/**
	 * Adds the Config button to this mod's entry in the Mods list.
	 *
	 * <p>Guarded on YACL actually being loaded: {@code YaclConfigScreen} and {@code BarPreview} are
	 * the only classes that touch YACL types, and neither is referenced unless we get past this
	 * check, so the mod runs fine without the library present.
	 */
	private static void registerConfigScreen(ModContainer container) {
		if (!ModList.get().isLoaded(EmiTreeTabs.YACL)) {
			EmiTreeTabs.LOGGER.info("[emitreetabs] {} not present, edit config/{}.json by hand",
					EmiTreeTabs.YACL, EmiTreeTabs.MOD_ID);
			return;
		}
		try {
			container.registerExtensionPoint(IConfigScreenFactory.class,
					(IConfigScreenFactory) (mod, parent) -> YaclConfigScreen.create(parent));
			// Also reachable from the sidebar's own settings button, without going out to the
			// Mods list first.
			ConfigScreenHook.set(YaclConfigScreen::create);
		} catch (Throwable t) {
			// A config button is not worth taking the game down for.
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not register the config screen", t);
		}
	}
}
