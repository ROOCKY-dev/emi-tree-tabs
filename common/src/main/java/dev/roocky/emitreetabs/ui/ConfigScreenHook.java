package dev.roocky.emitreetabs.ui;

import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * How the shared code opens the settings screen without knowing which loader built it.
 *
 * <p>Each loader module supplies the factory during init, and only when Cloth Config is actually
 * installed. Everything here degrades to nothing when it is not: the settings button simply does
 * not appear, rather than appearing and failing.
 *
 * <p>A hook rather than another {@code PlatformHelper} method because it is optional twice over —
 * optional per loader, and optional per installed mod — and the platform interface is deliberately
 * the two things that are always there.
 */
public final class ConfigScreenHook {

	private static Function<Screen, Screen> factory;

	private ConfigScreenHook() {
	}

	/** Called by a loader module at init, when a config screen can be built. */
	public static void set(Function<Screen, Screen> value) {
		factory = value;
	}

	/** Whether there is a settings screen to open at all. */
	public static boolean available() {
		return factory != null;
	}

	/** Opens the settings screen, returning to {@code parent} when it closes. */
	public static void open(Screen parent) {
		if (factory == null) {
			return;
		}
		Screen screen = factory.apply(parent);
		if (screen != null) {
			Minecraft.getInstance().setScreen(screen);
		}
	}
}
