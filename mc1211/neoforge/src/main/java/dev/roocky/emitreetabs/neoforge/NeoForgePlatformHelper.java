package dev.roocky.emitreetabs.neoforge;

import java.nio.file.Path;

import dev.roocky.emitreetabs.platform.PlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/** Found by ServiceLoader; see META-INF/services. */
public class NeoForgePlatformHelper implements PlatformHelper {

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
}
