package dev.ahmed.emitreetabs.forge;

import java.nio.file.Path;

import dev.ahmed.emitreetabs.platform.PlatformHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

/** Found by ServiceLoader; see META-INF/services. */
public class ForgePlatformHelper implements PlatformHelper {

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
}
