package dev.roocky.emitreetabs.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * Resolves the loader-specific {@link PlatformHelper} through {@link ServiceLoader}.
 *
 * <p>Each loader module ships a {@code META-INF/services} entry pointing at its implementation.
 * Plain JDK service loading is enough here and keeps the mod free of any third-party runtime
 * dependency for the sake of two methods.
 */
public final class Platform {

	private static final PlatformHelper HELPER = ServiceLoader.load(PlatformHelper.class)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
					"No PlatformHelper on the classpath; the loader module is missing its "
							+ "META-INF/services entry"));

	private Platform() {
	}

	public static Path configDir() {
		return HELPER.configDir();
	}

	public static boolean isModLoaded(String modId) {
		return HELPER.isModLoaded(modId);
	}
}
