package dev.roocky.emitreetabs.fabric;

import java.nio.file.Path;

import dev.roocky.emitreetabs.platform.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

/** Found by ServiceLoader; see META-INF/services. */
public class FabricPlatformHelper implements PlatformHelper {

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}
}
