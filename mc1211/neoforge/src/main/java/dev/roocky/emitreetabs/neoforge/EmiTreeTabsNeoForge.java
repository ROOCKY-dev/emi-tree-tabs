package dev.roocky.emitreetabs.neoforge;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.roocky.emitreetabs.config.YaclConfigScreen;
import dev.roocky.emitreetabs.ui.ConfigScreenHook;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge entrypoint. Everything of substance lives in the shared module.
 *
 * <p>The 1.20.1 build uses Forge, which stopped getting an EMI build after 1.20.x; this is the same
 * role for 1.21.1, so the differences from the Forge entrypoint are the ones NeoForge forces -
 * the mod container arrives in the constructor rather than being looked up, and the config screen is
 * an {@code IConfigScreenFactory} rather than a {@code ConfigScreenHandler.ConfigScreenFactory}.
 */
@Mod(EmiTreeTabs.MOD_ID)
public class EmiTreeTabsNeoForge {

	public EmiTreeTabsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		EmiTreeTabs.initClient();
		registerConfigScreen(modContainer);
	}

	/**
	 * Adds the Config button to this mod's entry in the Mods list.
	 *
	 * <p>Guarded on YACL actually being loaded: {@code YaclConfigScreen} and {@code BarPreview} are
	 * the only classes that touch YACL types, and neither is referenced unless we get past this
	 * check, so the mod runs fine without the library present.
	 */
	private void registerConfigScreen(ModContainer modContainer) {
		if (!net.neoforged.fml.ModList.get().isLoaded(EmiTreeTabs.YACL)) {
			EmiTreeTabs.LOGGER.info("[emitreetabs] {} not present, edit config/{}.json by hand",
					EmiTreeTabs.YACL, EmiTreeTabs.MOD_ID);
			return;
		}
		try {
			// The cast picks between ModContainer's two overloads, registerExtensionPoint(Class, T)
			// and registerExtensionPoint(Class, Supplier<T>) - a bare lambda is ambiguous between
			// them, because an IConfigScreenFactory is itself a functional interface.
			IConfigScreenFactory factory = (container, parent) -> YaclConfigScreen.create(parent);
			modContainer.registerExtensionPoint(IConfigScreenFactory.class, factory);
			// Also reachable from the sidebar's own settings button, without going out to the
			// Mods list first.
			ConfigScreenHook.set(YaclConfigScreen::create);
		} catch (Throwable t) {
			// A config button is not worth taking the game down for.
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not register the config screen", t);
		}
	}
}
