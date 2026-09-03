package dev.ahmed.emitreetabs.platform;

import java.nio.file.Path;

/**
 * The only things the shared code needs from a mod loader.
 *
 * <p>Deliberately tiny. Events are <em>not</em> here: each loader registers its own and calls into
 * the shared code, which is less machinery than an abstraction over three different event systems
 * would cost.
 */
public interface PlatformHelper {

	/** The instance's config directory, where this mod's json files live. */
	Path configDir();

	/** Whether another mod is present, used for the optional Cloth Config screen. */
	boolean isModLoaded(String modId);
}
