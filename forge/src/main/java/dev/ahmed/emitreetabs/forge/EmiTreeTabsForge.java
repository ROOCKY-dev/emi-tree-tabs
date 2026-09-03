package dev.ahmed.emitreetabs.forge;

import dev.ahmed.emitreetabs.EmiTreeTabs;
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
	 * <p>Guarded on Cloth Config actually being loaded: {@link ClothConfigScreen} is the only class
	 * that touches Cloth types, and it is never referenced unless we get past this check, so the
	 * mod runs fine without the library present.
	 */
	private void registerConfigScreen() {
		if (!ModList.get().isLoaded(EmiTreeTabs.CLOTH_CONFIG)) {
			EmiTreeTabs.LOGGER.info("[emitreetabs] {} not present, edit config/{}.json by hand",
					EmiTreeTabs.CLOTH_CONFIG, EmiTreeTabs.MOD_ID);
			return;
		}
		try {
			ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new ConfigScreenHandler.ConfigScreenFactory(
							(client, parent) -> ClothConfigScreen.create(parent)));
		} catch (Throwable t) {
			// A config button is not worth taking the game down for.
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not register the config screen", t);
		}
	}
}
