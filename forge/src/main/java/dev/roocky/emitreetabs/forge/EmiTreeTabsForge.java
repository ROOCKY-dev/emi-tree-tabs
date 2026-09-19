package dev.roocky.emitreetabs.forge;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.roocky.emitreetabs.config.YaclConfigScreen;
import dev.roocky.emitreetabs.ui.ConfigScreenHook;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge entrypoint. Everything of substance lives in the shared module. */
@Mod(EmiTreeTabs.MOD_ID)
public class EmiTreeTabsForge {

	public EmiTreeTabsForge() {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		EmiTreeTabs.initClient();
		registerConfigScreen();
	}

	/**
	 * Adds the Config button to this mod's entry in the Mods list.
	 *
	 * <p>Guarded on YACL actually being loaded: {@code YaclConfigScreen} and {@code BarPreview} are
	 * the only classes that touch YACL types, and neither is referenced unless we get past this
	 * check, so the mod runs fine without the library present.
	 */
	private void registerConfigScreen() {
		if (!ModList.get().isLoaded(EmiTreeTabs.YACL)) {
			EmiTreeTabs.LOGGER.info("[emitreetabs] {} not present, edit config/{}.json by hand",
					EmiTreeTabs.YACL, EmiTreeTabs.MOD_ID);
			return;
		}
		try {
			ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new ConfigScreenHandler.ConfigScreenFactory(
							(client, parent) -> YaclConfigScreen.create(parent)));
			// Also reachable from the sidebar's own settings button, without going out to the
			// Mods list first.
			ConfigScreenHook.set(YaclConfigScreen::create);
		} catch (Throwable t) {
			// A config button is not worth taking the game down for.
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not register the config screen", t);
		}
	}
}
